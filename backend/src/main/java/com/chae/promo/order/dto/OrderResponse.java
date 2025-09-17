package com.chae.promo.order.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

public class OrderResponse {

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Purchase{
        private String publicId; // 주문 공개 ID
        private Long customerId; // 회원 ID (비회원일 경우 NULL)
        private String ordererName; // 주문자 이름 (비회원일 때 주문자이름 저장)
        private BigDecimal totalPrice; // 총 결제 금액
        private String status; // 주문 상태
        private List<PurchaseItem> orderItems; // 주문 상품 목록
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PurchaseItem {
        private String productCode; // 상품 코드
        private String productName; // 상품 이름
        private BigDecimal price; // 상품 가격
        private long quantity; // 주문 수량
        private BigDecimal totalPrice; // 총 가격 (price * quantity)
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class OrderSummary{
        private String publicId; // 주문 공개 ID
        private BigDecimal totalPrice; // 총 결제 금액
        private long itemCount; // 주문 상품 개수
        private String status; // 주문 상태
    }

}
