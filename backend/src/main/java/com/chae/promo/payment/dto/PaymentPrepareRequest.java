package com.chae.promo.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PaymentPrepareRequest(
        @NotBlank
        String orderId, // 주문 ID

        @NotNull
        PaymentMethodRequest paymentMethod // 결제 수단

) {
}
