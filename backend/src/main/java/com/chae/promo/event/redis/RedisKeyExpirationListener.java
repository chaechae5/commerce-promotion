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

    private final EventRedisKeyManager keyManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Redis 키 만료 이벤트 수신: {}", message.toString());
        String expiredKey = message.toString();

        if (keyManager.isEventStartFlagKey(expiredKey)) {
            String eventId = keyManager.extractEventId(expiredKey);
            //  중복 방지: 이미 OPEN 상태면 skip
            if (eventService.isAlreadyOpened(eventId)) {
                log.info("이미 OPEN 상태, Skip: eventId={}", eventId);
                return;
            }
            handleEventStart(eventId);
        }
    }

    private void handleEventStart(String eventId) {
        log.info("Redis TTL 만료 감지됨: eventId={}", eventId);
        eventService.markEventAsOpened(eventId);
        eventKafkaPublisher.publishEventOpen(eventId);

        eventService.removeFromSchedule(eventId);


    }


}
