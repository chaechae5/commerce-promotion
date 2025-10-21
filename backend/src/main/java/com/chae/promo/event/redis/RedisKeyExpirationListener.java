package com.chae.promo.event.redis;

import com.chae.promo.event.kafka.EventKafkaPublisher;
import com.chae.promo.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisKeyExpirationListener implements MessageListener {

    private final EventKafkaPublisher eventKafkaPublisher;
    private final EventService eventService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        // event:1001:start_flag 형태만 처리
        if (expiredKey.startsWith("event:") && expiredKey.endsWith(":start_flag")) {
            String eventId = extractEventId(expiredKey);

            //  중복 방지: 이미 OPEN 상태면 skip
            if (eventService.isAlreadyOpened(eventId)) {
                log.info("이미 OPEN 상태, Skip: eventId={}", eventId);
                return;
            }
            handleEventStart(eventId);
        }
    }

    private void handleEventStart(String eventId) {
        if (!eventService.isAlreadyOpened(eventId)) {
            log.info("Redis TTL 만료 감지됨: eventId={}", eventId);
            eventService.markEventAsOpened(eventId);
            eventKafkaPublisher.publishEventOpen(eventId);
        } else {
            log.info("eventId={} 이미 오픈 상태, 중복 방지됨", eventId);
        }
    }

    /** event:1001:start_flag → 1001 추출 */
    private String extractEventId(String key) {
        try {
            return key.split(":")[1];
        } catch (Exception e) {
            log.warn("키 파싱 실패, key={}", key);
            return "UNKNOWN";
        }
    }
}
