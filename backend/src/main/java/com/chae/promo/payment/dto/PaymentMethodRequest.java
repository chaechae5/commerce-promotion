package com.chae.promo.payment.dto;

import com.chae.promo.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;


public record PaymentMethodRequest(
        @Schema(description = "결제 수단", example = "CARD")
        @NotNull
        PaymentMethod method,

        @Schema(description = "결제 금액", example = "10000")
        @NotNull
        BigDecimal amount
) {
}
