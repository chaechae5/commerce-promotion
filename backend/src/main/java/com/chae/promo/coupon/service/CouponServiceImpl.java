package com.chae.promo.coupon.service;

import com.chae.promo.common.jwt.JwtProvider;
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
        String userId = jwtProvider.validateAndGetAnonId(token);

        Coupon coupon = couponRepository.findByCode("LABUBUISCOMMING")
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND));

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
        //일단 TTL 임의로 설정
        int couponExpireDays = 1;
        redisTemplate.opsForValue().set(userCouponKey, "issued", couponExpireDays, TimeUnit.DAYS);


        //DB에 저장
        CouponIssue issue = CouponIssue.builder()
                .coupon(coupon)
                .userId(userId)
                .issuedAt(LocalDateTime.now())
                .expireAt(coupon.getEndDate())
                .status(CouponIssueStatus.ISSUED)
                .build();

        try{
            couponIssueRepository.save(issue);
            log.info("DB: 쿠폰발급 user: {} couponCode: {} couponIssueId: {}", userId, couponCode, issue.getId());
        }catch (Exception e) {
            log.error("DB 발급 저장 실패 user: {}, couponCode: {}", userId, couponCode, e);
            redisTemplate.opsForValue().increment(stockKey, 1);
            redisTemplate.delete(userCouponKey);
            throw new CommonCustomException(CommonErrorCode.COUPON_ISSUE_SAVE_FAIL);
        }

        return CouponResponse.Issue.builder()
                .couponIssueId(issue.getId())
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .issuedAt(issue.getIssuedAt())
                .expireAt(issue.getExpireAt())
                .status(issue.getStatus().name())
                .build();

    }
}
