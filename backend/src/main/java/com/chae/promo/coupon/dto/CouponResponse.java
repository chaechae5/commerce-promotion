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
        private Long couponIssueId;
        private String code;
        private String name;
        private String description;
        private LocalDateTime issuedAt;
        private LocalDateTime expireAt;
        private String status;
    }
}
