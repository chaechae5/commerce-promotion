package com.chae.promo.coupon.service;

import com.chae.promo.security.JwtUtil;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.coupon.dto.CouponRedisRequest;
import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.repository.CouponIssueRepository;
import com.chae.promo.coupon.repository.CouponRepository;
import com.chae.promo.coupon.service.redis.CouponRedisKeyManager;
import com.chae.promo.coupon.service.redis.CouponRedisService;
import com.chae.promo.coupon.util.CouponExpirationCalculator;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final JwtUtil jwtUtil;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    private final CouponRedisService couponRedisService;
    private final CouponExpirationCalculator couponExpirationCalculator;
    private final CouponRedisKeyManager couponRedisKeyManager;
    private final CouponIssuePersistenceService couponIssuePersistenceService;

    @Override
    public CouponResponse.Issue issueCoupon(String userId) {
        //coupon id - 쿠폰 종류 1개만 있다고 가정
        String couponCode = "LABUBUISCOMMING";

        //토큰 검증 및 user id
        String userId = validateTokenAndExtractPrincipalId(token);

        // Redis Key 생성
        String couponStockKey = couponRedisKeyManager.getCouponStockKey(couponCode);
        String userCouponKey = couponRedisKeyManager.getUserCouponKey(userId, couponCode);
        String couponTtlKey = couponRedisKeyManager.getCouponTtlKey(couponCode);

        Coupon coupon = findCoupon(couponCode);

        LocalDateTime calculatedExpireAt;
        long ttlSeconds;
        LocalDateTime now = LocalDateTime.now(); //기준시점
        try {
            calculatedExpireAt = getCouponExpirationDateTime(coupon);
            ttlSeconds = couponExpirationCalculator.calculateTtlSeconds(calculatedExpireAt, now);
        } catch (CommonCustomException e) {
            log.warn("쿠폰 만료일이 지났습니다. 발급 중단. couponCode: {}", couponCode);
            throw e;
        }

        // Redis에 재고가 없으면 DB에서 불러와 캐시
        initializeCouponCacheIfNeeded(coupon, couponStockKey, couponTtlKey, ttlSeconds);

        CouponRedisRequest couponRedisRequest = CouponRedisRequest.builder()
                .coupon(coupon)
                .couponStockKey(couponStockKey)
                .userCouponKey(userCouponKey)
                .couponTtlKey(couponTtlKey)
                .userId(userId)
                .couponIssueStatus(CouponIssueStatus.ISSUED)
                .ttlSeconds(ttlSeconds)
                .build();

        try {
            // Redis Lua 스크립트를 사용하여 원자적으로 쿠폰 확인, 재고 차감 및 발급 상태 저장
            couponRedisService.issueCouponAtomically(couponRedisRequest);
        } catch (DataAccessException e) {
            //Redis 시스템 장애 처리 (연결 실패, 타입 불일치 등)
            log.error("Redis 작업 중 시스템 예외 발생. userId: {}, couponCode: {}", userId, couponCode, e);
            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_DATA_ACCESS_FAIL);
        }

        try {
            //쿠폰 publicId 생성 (uuid)
            String publicId = UuidUtil.generate();

            //DB에 저장 - couponIssuePersistenceService로 위임
            CouponIssue issue = couponIssuePersistenceService.saveCouponIssue(coupon, userId, calculatedExpireAt, publicId);

            return CouponResponse.Issue.builder()
                    .couponIssueId(issue.getPublicId()) // publicId로 노출
                    .code(coupon.getCode())
                    .name(coupon.getName())
                    .description(coupon.getDescription())
                    .issuedAt(issue.getIssuedAt())
                    .expireAt(issue.getExpireAt())
                    .status(issue.getStatus().name())
                    .build();

        } catch (Exception e) {
            // DB 저장 실패 시 Redis 롤백
            log.error("DB 저장 실패로 Redis 롤백 수행. userId: {}, couponCode: {}", userId, couponCode, e);
            couponRedisService.rollbackRedisCouponStock(couponStockKey, userCouponKey);

            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
        }

    }

    private LocalDateTime getCouponExpirationDateTime(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        return couponExpirationCalculator.calculateExpiration(coupon, now);
    }

    private String validateTokenAndExtractPrincipalId(String token) {
        Claims claims = jwtUtil.validateToken(token);
        return claims.get("principalId", String.class);
    }

    private Coupon findCoupon(String couponCode) {
        return couponRepository.findByCode(couponCode)
                .orElseThrow(() -> {
                    log.warn("쿠폰 조회 실패 couponCode: {}", couponCode);
                    return new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND);
                });
    }

    /**
     * 쿠폰 발급에 필요한 재고와 TTL 정보를 Redis에 설정
     */
    private void initializeCouponCacheIfNeeded(Coupon coupon, String couponStockKey, String couponTtlKey, long ttlSeconds) {
        //재고 키 확인 및 생성
        if (Boolean.FALSE.equals(couponRedisService.hasKey(couponStockKey))) {
            log.info("Redis에 쿠폰 재고 {}가 없습니다. DB에서 로드하여 캐시합니다.", couponStockKey);

            initializeCouponStockFromDB(coupon, couponStockKey);
        }

        //ttl 키 확인 및 생성
        if (Boolean.FALSE.equals(couponRedisService.hasKey(couponTtlKey))) {
            log.info("Redis에 쿠폰 TTL 키 [{}]가 없습니다. 만료 시간을 설정합니다.", couponTtlKey);

            // 남은 시간이 있는 경우에만 TTL 키 생성
            if (ttlSeconds > 0) {
                couponRedisService.setCouponTtlKey(couponTtlKey, ttlSeconds);
            } else {
                log.warn("쿠폰 [{}]의 유효 기간이 이미 만료되었습니다. TTL 키를 생성하지 않습니다.", coupon.getCode());
            }
        }
    }


    private void initializeCouponStockFromDB(Coupon coupon, String couponStockKey) {
        // 실제 남은 재고 = 전체 수량 - 이미 발급된 수량
        long issuedCount = couponIssueRepository.countByCouponIdAndStatus(
                coupon.getId(), CouponIssueStatus.ISSUED);

        long remainingStock = Math.max(0, coupon.getTotalQuantity() - issuedCount);

        couponRedisService.setCouponStock(couponStockKey, remainingStock);
        log.info("Redis 재고 초기화 완료. couponCode: {}, 전체={}, 발급됨={}, 남은재고={}",
                coupon.getCode(), coupon.getTotalQuantity(), issuedCount, remainingStock);
    }

}
