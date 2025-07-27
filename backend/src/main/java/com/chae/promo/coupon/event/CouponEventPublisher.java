package com.chae.promo.coupon.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String COUPON_ISSUED_TOPIC = Topic.Names.COUPON_ISSUED;

    public void publishCouponIssued(CouponIssuedEvent event) {
        kafkaTemplate.send(COUPON_ISSUED_TOPIC, event.getUserId(), event);
        log.info("쿠폰 발급 이벤트 발행: {}", event);
    }
}