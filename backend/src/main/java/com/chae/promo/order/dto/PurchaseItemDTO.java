package com.chae.promo.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PurchaseItemDTO {
    @NotBlank(message = "상품코드는 필수입니다.")
    private String productCode; // 상품코드

    @Positive(message = "구매 수량은 1개 이상이어야 합니다.")
    private long quantity; //구매 수량
}
