package com.chae.promo.coupon.event;

import com.chae.promo.common.kafka.TopicNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCouponIssued(CouponIssuedEvent event) {
        kafkaTemplate.send(TopicNames.COUPON_ISSUED, event.getUserId(), event);
        log.info("쿠폰 발급 이벤트 발행: {}", event);
    }
}