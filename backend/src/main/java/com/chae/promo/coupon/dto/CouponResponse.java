package com.chae.promo.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class CouponResponse {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Issue{
        private String couponIssueId; //쿠폰 발급 publicId
        private String code; //쿠폰코드
        private String name; //쿠폰명
        private String description; //쿠폰 description
        private LocalDateTime issuedAt;
        private LocalDateTime expireAt;
        private String status; //쿠폰 상태
    }
}
