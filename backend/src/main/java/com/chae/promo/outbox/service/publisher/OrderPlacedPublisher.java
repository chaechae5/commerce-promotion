package com.chae.promo.outbox.service.publisher;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.order.event.OrderPlacedEvent;
import com.chae.promo.order.event.OrderPlacedEventPublisher;
import com.chae.promo.outbox.service.DomainOutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedPublisher implements DomainOutboxPublisher {

    private final ObjectMapper objectMapper;
    private final OrderPlacedEventPublisher orderPlacedEventPublisher;


    @Override
    public boolean supports(String eventType) {
        return eventType.equals(TopicNames.ORDER_PLACED);
    }

    @Override
    public void publish(String payloadJson) throws Exception {
        OrderPlacedEvent event =
                objectMapper.readValue(payloadJson, OrderPlacedEvent.class);

        // 이벤트 발행
        orderPlacedEventPublisher.publishOrderPlacedSync(event);
        log.info("[Publisher] ORDER_PLACED published eventId={}, orderPublicId={}", event.getEventId(), event.getOrderPublicId());

    }
}
