package com.chae.promo.event.kafka;

import com.chae.promo.common.kafka.TopicNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 이벤트 오픈 이벤트 발행
     * @param outboxEventId outbox 이벤트 ID
     * @param eventDomainId 도메인 이벤트 ID
     */
    public void publishEventOpen(String outboxEventId, String eventDomainId) {
        kafkaTemplate.send(TopicNames.EVENT_OPEN, outboxEventId, eventDomainId);
        log.info("Kafka EVENT_OPEN 발행됨 (outboxEventId={}, eventDomainId={})", outboxEventId, eventDomainId);
    }
}
