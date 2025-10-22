package com.chae.promo.common.kafka;

/**
 * Kafka 토픽을 관리하는 Enum.
 * 각 Enum 상수는 실제 토픽 이름을 값으로 가집니다.
 */
public final class TopicNames {
    public static final String COUPON_ISSUED = "coupon.issued"; //쿠폰 발급

    public static final String ORDER_PLACED = "order.placed"; //주문 완료 (결제 완료 이후)

    public static final String PAYMENT_FAILED = "payment.failed"; //결제 승인 실패


    public static final String EVENT_OPEN = "event.open";

}