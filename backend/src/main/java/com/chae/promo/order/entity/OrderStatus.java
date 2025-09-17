package com.chae.promo.order.entity;

public enum OrderStatus {
    CREATED,        // 주문 생성
    PENDING_PAYMENT, // 결제 대기
    PAID,            // 결제 완료
    PREPARING_SHIPMENT, // 배송 준비중
    SHIPPED,         // 배송중
    DELIVERED,       // 배송 완료
    CANCELLED        // 주문 취소
}
