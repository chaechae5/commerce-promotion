package com.chae.promo.order.event;

import com.chae.promo.common.kafka.TopicNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 비동기 주문 완료 이벤트 발행
     * key : orderPublicId
     * @param event 주문 완료 이벤트
     */
    public void publishOrderPlaced(OrderPlacedEvent event) {
        kafkaTemplate.send(TopicNames.ORDER_PLACED, event.getOrderPublicId(), event);
        log.info("주문 완료 이벤트 발행(ASYNC). orderPublicId: {}, userId: {}", event.getOrderPublicId(), event.getUserId());
    }

    /**
     * 동기: key를 지정하여 주문 완료 이벤트 발행
     * @param key 이벤트 키
     * @param event 주문 완료 이벤트
     * */
    public void publishOrderPlacedSync(String key, OrderPlacedEvent event) {
        try {
            kafkaTemplate.send(TopicNames.ORDER_PLACED, key, event).get();
            log.info("주문 완료 이벤트 발행(SYNC). key: {}, orderPublicId: {}, userId: {}",
                    key, event.getOrderPublicId(), event.getUserId());
        } catch (Exception e) {
            throw new RuntimeException("Kafka publish failed", e);
        }
    }

    /**
     * 동기 : 멱등키(eventId)를 기본 key로 쓰는 동기 전송
     * @param event 주문 완료 이벤트
     */
    public void publishOrderPlacedSync(OrderPlacedEvent event) {
        publishOrderPlacedSync(event.getEventId(), event);
    }
}