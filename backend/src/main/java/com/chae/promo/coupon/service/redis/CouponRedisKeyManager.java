package com.chae.promo.coupon.service.redis;

import org.springframework.stereotype.Component;

@Component
public class CouponRedisKeyManager {

    // 특정 쿠폰의 남은 재고 (자료구조: String)
    private static final String COUPON_STOCK_KEY_FORMAT = "coupon:stock:%s"; //  %s = couponPublicId
    // 특정 사용자가 보유한 모든 쿠폰의 집합 (자료구조: Set)
    private static final String USER_COUPON_SET_KEY_FORMAT = "user:%s:coupon";  // %s = userId
    // 특정 쿠폰을 발급받은 모든 사용자의 집합 (자료구조: Set)
    private static final String COUPON_ISSUED_USER_SET_KEY_FORMAT = "coupon:issued_users:%s"; // %s = couponPublicId

    // 특정 쿠폰의 이벤트 유효 기간 (자료구조: String with TTL)
    private static final String COUPON_TTL_KEY_FORMAT = "coupon:ttl:%s"; // %s = couponPublicId

    /**
     * 쿠폰 재고 Key 생성
     *
     * @param couponPublicId 쿠폰 코드
     * @return Redis 쿠폰 재고 key
     */
    public String getCouponStockKey(String couponPublicId) {

        return String.format(COUPON_STOCK_KEY_FORMAT, couponPublicId);

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
     * 쿠폰 ttl Key 생성
     *
     * @param couponPublicId 쿠폰 코드
     * @return Redis 쿠폰 ttl key
     */

    public String getCouponTtlKey(String couponPublicId) {

        return String.format(COUPON_TTL_KEY_FORMAT, couponPublicId);
    }

    /**
     * 사용자 쿠폰 발급 상태 Set Key 생성
     *
     * @param couponPublicId 쿠폰 공개 ID
     * @return Redis 사용자 쿠폰 발급 상태 Set Key
     */
    public String getCouponIssuedUserSetKey(String couponPublicId) {

        return String.format(COUPON_ISSUED_USER_SET_KEY_FORMAT, couponPublicId);
    }

}

