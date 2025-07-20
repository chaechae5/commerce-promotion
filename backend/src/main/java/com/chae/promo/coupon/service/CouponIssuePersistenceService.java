package com.chae.promo.coupon.service;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.repository.CouponIssueRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssuePersistenceService {

    private final CouponIssueRepository couponIssueRepository;

    @Transactional
    public CouponIssue saveCouponIssue(Coupon coupon,
                                       String userId,
                                       LocalDateTime expireAt,
                                       String publicId) {

        CouponIssue issue = CouponIssue.builder()
                .coupon(coupon)
                .userId(userId)
                .issuedAt(LocalDateTime.now())
                .expireAt(expireAt)
                .status(CouponIssueStatus.ISSUED)
                .publicId(publicId)
                .build();


        CouponIssue savedIssue = couponIssueRepository.save(issue);
        log.info("쿠폰 발급 DB 저장 완료. userId: {}, couponCode: {}, couponIssueId: {}",
                userId, coupon.getCode(), savedIssue.getId());
        return savedIssue;

    }
}
