package com.chae.promo.payment.dto;

import com.chae.promo.payment.entity.PaymentMethod;

import java.time.LocalDateTime;

public record PaymentApprovalContext(
        boolean success,
        PaymentMethod paymentMethod,
        LocalDateTime paidAt,
        String failureMessage,
        boolean integrityFailed
) {
    public static PaymentApprovalContext success(PaymentMethod method, LocalDateTime paidAt, boolean integrityFailed) {
        return new PaymentApprovalContext(true, method, paidAt, null, integrityFailed);
    }

    public static PaymentApprovalContext failure(String message) {
        return new PaymentApprovalContext(false, null, null, message, false);
    }
}

