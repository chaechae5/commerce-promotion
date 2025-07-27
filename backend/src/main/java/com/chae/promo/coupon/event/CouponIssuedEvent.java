package com.chae.promo.coupon.event;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CouponIssuedEvent {
    private String eventId; // 이벤트 ID
    private String userId; // 사용자 ID
    private String couponPublicId;  // 쿠폰 코드 (공개 ID)
    private String couponIssueId; // 쿠폰 발급 ID
    private LocalDateTime issuedAt; // 쿠폰 발급 시간
    private LocalDateTime expiredAt; // 쿠폰 만료 시간
    private String userCouponSetKey; // Redis 사용자 쿠폰 키
    private String couponStockKey; // Redis 쿠폰 재고 키
    private String couponIssuedUserSetKey; // Redis 발급된 사용자 Set 키


    @Builder
    public CouponIssuedEvent(String eventId, String userId, String couponPublicId, String couponIssueId, LocalDateTime issuedAt, LocalDateTime expiredAt, String userCouponSetKey, String couponStockKey, String couponIssuedUserSetKey) {
        this.eventId = eventId;
        this.userId = userId;
        this.couponPublicId = couponPublicId;
        this.couponIssueId = couponIssueId;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
        this.userCouponSetKey =  userCouponSetKey;
        this.couponStockKey = couponStockKey;
        this.couponIssuedUserSetKey = couponIssuedUserSetKey;
    }
}
