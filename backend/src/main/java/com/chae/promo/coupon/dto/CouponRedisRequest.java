package com.chae.promo.coupon.dto;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponRedisRequest {
    private Coupon coupon;
    private String couponStockKey;
    private String userCouponKey;
    private String couponTtlKey;
    private String userId;
    private CouponIssueStatus couponIssueStatus;
    private long ttlSeconds;
}
