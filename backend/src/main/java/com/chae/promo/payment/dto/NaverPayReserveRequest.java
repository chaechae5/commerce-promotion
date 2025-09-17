package com.chae.promo.payment.dto;

public record NaverPayReserveRequest(
        String modelVersion,         // "2"
        String merchantUserKey,      // 내부 사용자 식별키(개인정보 X)
        String merchantPayKey,       // 내부 주문키(멱등/중복방지)
        String productName,
        int productCount,
        long totalPayAmount,
        String returnUrl,
        Long taxScopeAmount,
        Long taxExScopeAmount,
        String purchaserName,
        String purchaserBirthday
) {}

