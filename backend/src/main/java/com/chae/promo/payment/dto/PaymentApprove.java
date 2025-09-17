package com.chae.promo.payment.dto;

import com.chae.promo.payment.entity.PgType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentApprove {

    @NotBlank
    private String orderId; // 주문 ID

    @NotBlank
    private String paymentId; // 결제 ID (PG사에서 발급한 고유 ID)
}
