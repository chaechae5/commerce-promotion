package com.chae.promo.outbox.service.publisher;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.event.domain.EventOpenPayload;
import com.chae.promo.outbox.service.DomainOutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventOpenPublisher implements DomainOutboxPublisher {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;



    @Override
    public boolean supports(String eventType) {
        return eventType.equals(TopicNames.EVENT_OPEN);
    }

    @Override
    public void publish(String payloadJson) throws Exception {
        EventOpenPayload event = objectMapper.readValue(payloadJson, EventOpenPayload.class);

        // 이벤트 발행 - 동기로 처리 (outbox의 성공, 실패 처리를 위해)
        kafkaTemplate.send(TopicNames.EVENT_OPEN, event.getOutboxEventId(), event.getEventDomainId()).get();
        log.info("[Publisher] Kafka EVENT_OPEN 발행됨 (outboxEventId={}, eventDomainId={})", event.getOutboxEventId(), event.getEventDomainId());

    }
}
