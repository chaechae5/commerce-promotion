package com.chae.promo.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService{

    private final StringRedisTemplate redisTemplate;
    private static final String EVENT_PREFIX = "event";

    @Override
    public void scheduleEvent(String eventId, long delaySeconds) {
        String key = String.format("%s:%s:start_flag", EVENT_PREFIX, eventId);
        redisTemplate.opsForValue().set(key, "PENDING", Duration.ofSeconds(delaySeconds));

        log.info("이벤트 예약됨: eventId={}, delay={}s", eventId, delaySeconds);
    }

    @Override
    public void markEventAsOpened(String eventId) {
        String key = String.format("%s:%s:status", EVENT_PREFIX, eventId);
        redisTemplate.opsForValue().set(key, "OPEN");

        log.info("이벤트 오픈 상태로 갱신됨: eventId={}", eventId);
    }

    @Override
    public boolean isAlreadyOpened(String eventId) {
        String status = redisTemplate.opsForValue()
                .get(String.format("%s:%s:status", EVENT_PREFIX, eventId));
        return "OPEN".equals(status);
    }

    @Override
    public List<String> getExpiredPendingEvents() {
        Set<String> keys = redisTemplate.keys(EVENT_PREFIX + ":*:start_flag");
        if (keys == null) return Collections.emptyList();

        List<String> expired = new ArrayList<>();
        for (String key : keys) {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl <= 0) {
                String eventId = key.split(":")[1];
                expired.add(eventId);
            }
        }
        return expired;
    }
}
