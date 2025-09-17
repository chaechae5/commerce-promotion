package com.chae.promo.order.dto;

import com.chae.promo.order.validator.NoDuplicateProductCodes;
import com.chae.promo.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public class OrderRequest {

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Purchase{
        @Valid
        @NotEmpty(message = "구매 상품 목록은 비어 있을 수 없습니다.")
        @NoDuplicateProductCodes
        private List<PurchaseItemDTO> items;

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Create{

        @Valid
        @NotEmpty(message = "구매 상품 목록은 비어 있을 수 없습니다.")
        @NoDuplicateProductCodes
        List<PurchaseItemDTO> items;

        @Valid
        @NotNull(message = "배송지 정보는 필수입니다.")
        private ShippingInfo shippingInfo;

        private PaymentMethod paymentMethod;

    }


    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ShippingInfo {

        @NotBlank(message = "수령인 이름은 필수입니다.")
        @Schema(description = "수령인", example = "김초코", requiredMode = REQUIRED)
        private String recipientName;

        @NotBlank(message = "배송 주소는 필수입니다.")
        @Schema(description = "배송 주소", example = "서울특별시 용산구 한강대로", requiredMode = REQUIRED)
        private String address;

        @NotBlank(message = "우편번호는 필수입니다.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
        @Schema(description = "우편번호", example = "11111", requiredMode = REQUIRED)
        private String zipcode;

        @NotBlank(message = "연락처는 필수입니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        @Schema(description = "연락처(휴대전화)", example = "010-0000-0000", requiredMode = REQUIRED)
        private String phoneNumber;

        @Schema(description = "배송메모", requiredMode = NOT_REQUIRED)
        private String memo;
    }


}
