package com.chae.promo.event.kafka;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.sse.manager.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventOpenConsumer {
    private final SseEmitterManager emitterManager;

    private static final String EVENT_OPEN = "EVENT_OPEN";

    @KafkaListener(topics = TopicNames.EVENT_OPEN, groupId = "event.group")
    public void onEventOpen(String eventId) {
        try{
            log.info("EVENT_OPEN 수신됨: eventId={}", eventId);
            emitterManager.sendToAll(EVENT_OPEN, eventId);
        }catch (Exception e){
            log.error("SSE 전송 중 오류 발생: {}", e.getMessage(), e);
        }

    }
}
