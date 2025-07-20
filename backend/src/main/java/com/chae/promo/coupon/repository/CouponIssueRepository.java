package com.chae.promo.coupon.repository;

import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    long countByCouponIdAndStatus(long couponId, CouponIssueStatus couponIssueStatus);
}
