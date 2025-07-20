package com.chae.promo.coupon.service.redis;

import org.springframework.stereotype.Component;

@Component
public class CouponRedisKeyManager {

    private static final String COUPON_STOCK_KEY_FORMAT = "coupon:stock:%s"; // couponCode
    private static final String USER_COUPON_KEY_FORMAT = "coupon:user:%s:coupon:%s"; // userId, couponCode
    private static final String COUPON_TTL_KEY_FORMAT = "coupon:ttl:%s"; // couponCode


    /**
     * 쿠폰 재고 Key 생성
     *
     * @param couponCode 쿠폰 코드
     * @return Redis 쿠폰 재고 key
     */
    public String getCouponStockKey(String couponCode) {

        return String.format(COUPON_STOCK_KEY_FORMAT, couponCode);

    }

    /**
     * 사용자 쿠폰 발급 상태 Key 생성
     *
     * @param userId     사용자 ID
     * @param couponCode 쿠폰 코드
     * @return Redis 사용자 쿠폰 발급 상태 Key
     */

    public String getUserCouponKey(String userId, String couponCode) {

        return String.format(USER_COUPON_KEY_FORMAT, userId, couponCode);

    }


    /**
     * 쿠폰 ttl Key 생성
     *
     * @param couponCode 쿠폰 코드
     * @return Redis 쿠폰 ttl key
     */

    public String getCouponTtlKey(String couponCode) {

        return String.format(COUPON_TTL_KEY_FORMAT, couponCode);
    }

}

