package com.chae.promo.coupon.repository;

import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    long countByCouponIdAndStatus(long couponId, CouponIssueStatus couponIssueStatus);
    @Query("SELECT ci " +
            "FROM CouponIssue ci " +
            "JOIN FETCH ci.coupon c " +
            "WHERE ci.userId = :userId")
    List<CouponIssue> findByUserId(String userId);
}
