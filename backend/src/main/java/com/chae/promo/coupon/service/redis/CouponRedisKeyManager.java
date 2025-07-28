package com.chae.promo.coupon.service.redis;

import org.springframework.stereotype.Component;

@Component
public class CouponRedisKeyManager {

    // 특정 쿠폰의 남은 재고 (자료구조: String)
    private static final String COUPON_STOCK_KEY_FORMAT = "coupon:stock:%s:%s"; //  %s = couponPublicId, %s = couponCode
    // 특정 사용자가 보유한 모든 쿠폰의 집합 (자료구조: Set)
    private static final String USER_COUPON_SET_KEY_FORMAT = "user:%s:coupon";  // %s = userId
    // 특정 쿠폰을 발급받은 모든 사용자의 집합 (자료구조: Set)
    private static final String COUPON_ISSUED_USER_SET_KEY_FORMAT = "coupon:issued_users:%s:%s"; // %s = couponPublicId,  %s = couponCode

    // 특정 쿠폰의 이벤트 유효 기간 (자료구조: String with TTL)
    private static final String COUPON_TTL_KEY_FORMAT = "coupon:ttl:%s:%s"; // %s = couponPublicId,  %s = couponCode


    /**
     * 쿠폰 재고 Key 생성
     * @param couponPublicId
     * @param couponCode
     * @return
     */
    public String getCouponStockKey(String couponPublicId, String couponCode) {

        return String.format(COUPON_STOCK_KEY_FORMAT, couponPublicId, couponCode);

    }

    /**
     * 사용자 쿠폰 Set Key 생성
     *
     * @param userId 사용자 ID
     * @return Redis 사용자 쿠폰 Set Key
     */
    public String getUserCouponSetKey(String userId) {

        return String.format(USER_COUPON_SET_KEY_FORMAT, userId);

    }



    /**
     * 쿠폰 TTL Key 생성
     *
     * @param couponPublicId 쿠폰 공개 ID
     * @param couponCode 쿠폰 코드
     * @return Redis 쿠폰 TTL Key
     */

    public String getCouponTtlKey(String couponPublicId, String couponCode){

        return String.format(COUPON_TTL_KEY_FORMAT, couponPublicId, couponCode);
    }


    /**
     * 쿠폰 발급된 사용자 Set Key 생성
     *
     * @param couponPublicId 쿠폰 공개 ID
     * @param couponCode 쿠폰 코드
     * @return Redis 쿠폰 발급된 사용자 Set Key
     */
    public String getCouponIssuedUserSetKey(String couponPublicId, String couponCode) {

        return String.format(COUPON_ISSUED_USER_SET_KEY_FORMAT, couponPublicId, couponCode);
    }

}

