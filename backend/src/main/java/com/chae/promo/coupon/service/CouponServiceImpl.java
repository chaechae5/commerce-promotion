package com.chae.promo.coupon.service;

import com.chae.promo.common.jwt.JwtProvider;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.repository.CouponIssueRepository;
import com.chae.promo.coupon.repository.CouponRepository;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService{

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;


    @Transactional
    @Override
    public CouponResponse.Issue issueCoupon(String token) {
        //coupon id - 쿠폰 종류 1개만 있다고 가정
        String couponCode = "LABUBUISCOMMING";

        //토큰 검증 및 user id
        String userId = validateToken(token);
        Coupon coupon = findCoupon(couponCode);

        //redis key
        String userCouponKey = "coupon:user:" + userId + ":coupon:" + couponCode;
        String stockKey = "coupon:stock:" + couponCode;

        //redis 중복 발급
        Boolean alreadyIssued = redisTemplate.hasKey(userCouponKey);
        if (Boolean.TRUE.equals(alreadyIssued)) {
            log.info("쿠폰 중복발급  userCouponKey: {}", userCouponKey);
            throw new CommonCustomException(CommonErrorCode.COUPON_ALREADY_ISSUED);
        }

        //redis 재고 차감
        Long stock = redisTemplate.opsForValue().decrement(stockKey);
        if (stock == null || stock < 0) {
            log.info("쿠폰 소진 couponCode: {}", couponCode);
            throw new CommonCustomException(CommonErrorCode.COUPON_SOLD_OUT);
        }

        //redis 발급 상태 저장
        LocalDateTime calculatedExpireAt = getCouponExpirationDateTime(coupon);
        saveRedisCouponIssue(userCouponKey, calculatedExpireAt);

        //쿠폰 publicId 생성 (uuid)
        String publicId = UuidUtil.generate();

        //DB에 저장
        CouponIssue issue = saveCouponIssue(coupon, userId, calculatedExpireAt, stockKey, userCouponKey, publicId);

        return CouponResponse.Issue.builder()
                .couponIssueId(issue.getPublicId()) // publicId로 노출
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .issuedAt(issue.getIssuedAt())
                .expireAt(issue.getExpireAt())
                .status(issue.getStatus().name())
                .build();

    }


    private String validateToken(String token){
        return jwtProvider.validateAndGetAnonId(token);
    }

    private Coupon findCoupon(String couponCode){
        return couponRepository.findByCode(couponCode)
                .orElseThrow(() -> {
                    log.warn("쿠폰 조회 실패 couponCode: {}", couponCode);
                    return new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND);
                });
    }

    private LocalDateTime getCouponExpirationDateTime(Coupon coupon) {
        if (coupon.getValidDays() != null && coupon.getValidDays() > 0) {
            return LocalDateTime.now().plusDays(coupon.getValidDays());
        } else if (coupon.getExpireDate() != null) {
            return coupon.getExpireDate();
        } else {
            // 둘 다 없는 경우, 기본 만료일 또는 오류 처리
            log.warn("쿠폰 {}의 유효 일수 및 만료일이 설정되지 않았습니다. 기본 7일 유효 기간 적용.", coupon.getCode());
            return LocalDateTime.now().plusDays(7);
        }
    }

    private long calculateTtlSeconds(LocalDateTime expireAt){
        Duration duration = Duration.between(LocalDateTime.now(), expireAt);
        long ttl = duration.getSeconds();
        if (ttl < 0) {
            log.warn("쿠폰 만료 TTL < 0. 1초로 설정");
            return 1;
        }
        return ttl;
    }

    private void saveRedisCouponIssue(String userCouponKey,LocalDateTime expireAt) {
        long ttlSeconds = calculateTtlSeconds(expireAt);
        redisTemplate.opsForValue().set(userCouponKey, "issued", ttlSeconds, TimeUnit.SECONDS);
        log.debug("쿠폰 발급 상태 저장 완료. key: {}, ttl: {}초", userCouponKey, ttlSeconds);
    }

    private CouponIssue saveCouponIssue(Coupon coupon,
                                        String userId,
                                        LocalDateTime expireAt,
                                        String stockKey,
                                        String userCouponKey,
                                        String publicId) {

        CouponIssue issue = CouponIssue.builder()
                .coupon(coupon)
                .userId(userId)
                .issuedAt(LocalDateTime.now())
                .expireAt(expireAt)
                .status(CouponIssueStatus.ISSUED)
                .publicId(publicId)
                .build();

        try {
            CouponIssue savedIssue = couponIssueRepository.save(issue);
            log.info("쿠폰 발급 DB 저장 완료. userId: {}, couponCode: {}, couponIssueId: {}",
                    userId, coupon.getCode(), savedIssue.getId());
            return savedIssue;

        } catch (Exception e) {
            log.error("쿠폰 발급 DB 저장 실패. userId: {}, couponCode: {}", userId, coupon.getCode(), e);
            rollbackRedisCouponStock(stockKey, userCouponKey);
            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
        }


    }

    private void rollbackRedisCouponStock(String stockKey, String userCouponKey) {
        try {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.delete(userCouponKey);
            log.info("Redis 롤백 완료. stockKey: {}, userCouponKey: {}",
                    stockKey, userCouponKey);
        } catch (Exception rollbackException) {
            log.error("Redis 롤백 실패. stockKey: {}, userCouponKey: {}",
                    stockKey, userCouponKey, rollbackException);
        }

    }


}
