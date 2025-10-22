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

    public void publishEventOpen(String eventId) {
        kafkaTemplate.send(TopicNames.EVENT_OPEN, eventId, "OPEN");
        log.info("Kafka EVENT_OPEN 발행됨 (eventId={})", eventId);
    }
}
