package com.chae.promo.coupon.service;

import com.chae.promo.coupon.dto.CouponResponse;

public interface CouponService {

    CouponResponse.Issue issueCoupon(String token);
}
