package com.chae.promo.outbox.service.publisher;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.event.domain.EventOpenPayload;
import com.chae.promo.event.kafka.EventKafkaPublisher;
import com.chae.promo.outbox.service.DomainOutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventOpenPublisher implements DomainOutboxPublisher {

    private final ObjectMapper objectMapper;
    private final EventKafkaPublisher eventKafkaPublisher;


    @Override
    public boolean supports(String eventType) {
        return eventType.equals(TopicNames.EVENT_OPEN);
    }

    @Override
    public void publish(String payloadJson) throws Exception {
        EventOpenPayload event = objectMapper.readValue(payloadJson, EventOpenPayload.class);

        // 이벤트 발행
        eventKafkaPublisher.publishEventOpen(event.getOutboxEventId(), event.getEventDomainId());
        log.info("[Publisher] EVENT_OPEN published eventDomainId={}", event.getEventDomainId());

    }
}
