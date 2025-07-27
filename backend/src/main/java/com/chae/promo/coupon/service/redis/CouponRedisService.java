package com.chae.promo.coupon.service.redis;

import com.chae.promo.coupon.dto.CouponRedisRequest;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRedisService {
    private final StringRedisTemplate stringRedisTemplate;

    // [발급] Redis 재고 차감 및 중복 발급 체크를 원자적으로 수행하는 Lua 스크립트
    // KEYS[1]: stockKey (재고 키)
    // KEYS[2]: userCouponSetKey (사용자별 보유 쿠폰 Set 키)
    // KEYS[3]: couponTtlKey (쿠폰 ttl 키)
    // KEYS[4]: couponIssuedUsersKey (발급된 사용자 Set 키)
    // ARGV[1]: userId (사용자 ID, 발급 상태 저장용)
    // ARGV[2]: couponPublicId (사용자 보유 쿠폰 Set에 저장할 값)
    // ARGV[3]: ttlSeconds (현재 사용되지 않지만, 필요에 따라 사용될 수 있음)
    // 반환 값: 1 (성공), 2 (재고 소진), 3 (중복 발급), 4 (쿠폰 만료), 0 (알 수 없는 오류)
    private static final String REDIS_ISSUE_COUPON_SCRIPT = """
            local couponStockKey = KEYS[1]
            local userCouponSetKey = KEYS[2]
            local couponTtlKey = KEYS[3]
            local couponIssuedUserSetKey = KEYS[4]
            local userId = ARGV[1]
            local couponPublicId = ARGV[2]
            local ttlSeconds = ARGV[3]
                        
            -- 1. 쿠폰 TTL 체크 : coupontTtlKey가 없으면(TTL 만료) 쿠폰 발급 불가
            if redis.call('EXISTS', couponTtlKey) == 0 then
                return 4 -- 쿠폰 기간 만료
            end
                        
            -- 2. 중복 발급 체크: 이미 발급된 쿠폰인지 확인
            if redis.call('SISMEMBER', couponIssuedUserSetKey, userId) == 1 then
                return 3 -- 중복 발급
            end
                        
            -- 3. 재고 차감: 현재 재고를 확인하고 0보다 클 경우에만 차감
            local updateStock = redis.call('DECR', couponStockKey)
                        
            if updateStock < 0 then
                -- 재고가 0이었는데 차감을 시도하여 음수가 된 경우, 재고 늘리기
                redis.call('INCR', couponStockKey)
                return 2
            end
                        
            -- 4. 발급 명단에 추가
            redis.call('SADD', couponIssuedUserSetKey, userId)
                       
            -- 5. 사용자별 보유 쿠폰 목록에 추가
            redis.call('SADD', userCouponSetKey, couponPublicId)
                      
            return 1 -- 성공
            """;

    // [롤백] 쿠폰 재고 복구 및 사용자 쿠폰 키 삭제를 원자적으로 수행하는 Lua 스크립트 정의
    // DB 저장 실패 시 Redis 상태를 롤백하는 데 사용
    // KEYS[1]: stockKey (재고 키 - 쿠폰 재고를 나타내는 Redis 키)
    // KEYS[2]: userCouponSetKey (사용자별 보유 쿠폰 Set 키)
    // KEYS[3]: couponIssuedUserSetKey (발급된 사용자 Set 키 - 쿠폰이 발급된 사용자의 ID를 저장하는 Redis 키)
    // ARGV[1]: userId
    // ARGV[2]: couponPublicId
    // 반환 값: 1 (성공), 0 (오류 - 이 스크립트는 항상 1을 반환하도록 설계됨)
    private static final String ROLLBACK_SCRIPT = """
    redis.call('INCR', KEYS[1]) -- KEYS[1]에 해당하는 쿠폰 재고를 1 증가 (차감된 재고 복구)
    redis.call('SREM', KEYS[3], ARGV[1]) -- KEYS[3]에서 사용자 ID를 제거 (발급된 사용자 목록에서 삭제)
    redis.call('SREM', KEYS[2], ARGV[2]) -- KEYS[2]에서 쿠폰 publicId를 제거 (사용자 보유 쿠폰 목록에서 삭제)
    return 1 // 스크립트 실행 성공을 나타내는 1을 반환
    """;
    private final DefaultRedisScript<Long> redisIssueCouponScript = new DefaultRedisScript<>(REDIS_ISSUE_COUPON_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> rollbackScript = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);

    /**
     * Redis Lua 스크립트를 사용하여 쿠폰 재고를 차감하고 사용자에게 발급
     * 원자적으로 실행
     *
     * @param couponRedisRequest 쿠폰 발급에 필요한 모든 정보를 담은 요청 객체
     * 포함 정보:
     * - couponStockKey: 쿠폰 재고를 관리하는 Redis 키
     * - userCouponSetKey: 사용자별 보유 쿠폰을 관리하는 Redis Set 키
     * - couponTtlKey: 쿠폰의 유효 기간을 나타내는 Redis 키 (이 키가 없으면 발급 불가)
     * - userId: 사용자 ID (로깅 및 스크립트 ARGV 전달용)
     * - ttlSeconds : userCouponKey의 만료 시간 (초)
     * - couponIssuedUserSetKey: 쿠폰 발급 사용자 집합 키
     */
    public void issueCouponAtomically(CouponRedisRequest couponRedisRequest) {


        List<String> keys = Arrays.asList(
                couponRedisRequest.getCouponStockKey(),
                couponRedisRequest.getUserCouponSetKey(),
                couponRedisRequest.getCouponTtlKey(),
                couponRedisRequest.getCouponIssuedUserSetKey()
        );
        String[] args = {couponRedisRequest.getUserId(),
                couponRedisRequest.getCoupon().getPublicId(),
                String.valueOf(couponRedisRequest.getTtlSeconds())
        };

        Long result = stringRedisTemplate.execute(redisIssueCouponScript, keys, args);

        handleRedisScriptResult(
                result,
                couponRedisRequest.getUserId(),
                couponRedisRequest.getCouponStockKey(),
                couponRedisRequest.getUserCouponSetKey(),
                couponRedisRequest.getCouponTtlKey(),
                couponRedisRequest.getCouponIssuedUserSetKey(),
                couponRedisRequest.getCoupon().getPublicId()
        );
    }

    /**
     * Lua 스크립트 실행 결과에 따라 적절한 예외를 처리합니다.
     *
     * @param result        Lua 스크립트 반환 값
     * @param userId        사용자 ID
     * @param couponStockKey      재고 키 (로깅용)
     * @param userCouponKey 사용자 쿠폰 발급 키 (로깅용)
     * @throws CommonCustomException 스크립트 결과에 따른 예외
     */
    private void handleRedisScriptResult(Long result,
                                         String userId,
                                         String couponStockKey,
                                         String userCouponKey,
                                         String couponTtlKey,
                                         String couponIssuedUserSetKey,
                                         String couponPublicId) {
        if (result == null) {
            log.error("Redis 스크립트 실행 결과가 NULL입니다. user: {}, stockKey: {}, userCouponKey: {}", userId, couponStockKey, userCouponKey);
            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
        }

        switch (result.intValue()) {
            case 1 -> // 성공
                    log.info("Redis: 쿠폰 발급 성공. userId: {}, couponPublicId: {}", userId, couponPublicId);
            case 2 -> { // 재고 소진
                log.info("Redis: 쿠폰 재고 소진. stockKey: {}, userId: {}", couponStockKey, userId);
                throw new CommonCustomException(CommonErrorCode.COUPON_SOLD_OUT);
            }
            case 3 -> { // 중복 발급
                log.info("Redis: 쿠폰 중복 발급. couponIssuedUserSetKey: {}, userId: {}", couponIssuedUserSetKey, userId);
                throw new CommonCustomException(CommonErrorCode.COUPON_ALREADY_ISSUED);
            }
            case 4 -> { //쿠폰 유효기간 만료
                log.info("Redis: TTL 만료. couponTtlKey: {}", couponTtlKey);
                throw new CommonCustomException(CommonErrorCode.COUPON_EXPIRED);
            }
            default -> { // 기타 알 수 없는 오류
                log.error("Redis: 알 수 없는 오류 발생. userId: {}, couponPublicId: {}, result: {}", userId, couponPublicId, result);
                throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
            }
        }
    }

    /**
     * Redis에 쿠폰 재고를 설정합니다.
     *
     * @param couponStockKey 쿠폰 재고 키
     * @param stock 재고 수량
     */
    public void setCouponStock(String couponStockKey, long stock) {
        stringRedisTemplate.opsForValue().set(couponStockKey, String.valueOf(stock));
        log.info("Redis: 쿠폰 재고 설정 완료. couponStockKey: {}, stock: {}", couponStockKey, stock);
    }

    public void rollbackRedisCouponStock(String couponStockKey, String userCouponSetKey, String couponIssuedUserSetKey, String userId, String couponPublicId) {
        try {
            List<String> keys = Arrays.asList(couponStockKey, userCouponSetKey, couponIssuedUserSetKey);
            String[] args = {userId, couponPublicId};
            stringRedisTemplate.execute(rollbackScript, keys, args);
            log.info("Redis 롤백 완료. stockKey: {}, userCouponSetKey: {}, couponIssuedUserSetKey: {}, userId: {}, couponPublicId: {}", couponStockKey, userCouponSetKey, couponIssuedUserSetKey, userId, couponPublicId);
        } catch (Exception e) {
            log.error("Redis 롤백 실패. stockKey: {}, userCouponSetKey: {}, couponIssuedUserSetKey: {}, userId: {}, couponPublicId: {}", couponStockKey, userCouponSetKey, couponIssuedUserSetKey, userId, couponPublicId, e);
        }
    }

    /**
     * 쿠폰 이벤트의 유효 기간을 나타내는 TTL 키를 생성합니다.
     * @param couponTtlKey 키
     * @param ttlSeconds 만료 시간(초)
     */
    public void setCouponTtlKey(String couponTtlKey, long ttlSeconds) {
        // 값은 의미 없으므로 "1"과 같은 더미 데이터를 사용
        stringRedisTemplate.opsForValue().set(couponTtlKey, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Redis에 해당 키가 존재하는지 확인합니다.
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }
}
