package com.chae.promo.coupon.service;

import com.chae.promo.coupon.dto.CouponResponse;

import java.util.List;

public interface CouponService {

    CouponResponse.Issue issueCoupon(String userId, String couponId);

    List<CouponResponse.Info> getAll();
    List<CouponResponse.Info> getMyCoupons(String userId);
}
