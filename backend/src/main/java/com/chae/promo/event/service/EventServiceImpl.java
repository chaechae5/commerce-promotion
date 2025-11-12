package com.chae.promo.event.service;

import com.chae.promo.event.redis.EventRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRedisRepository redisRepository;

    @Override
    public void scheduleEvent(String eventId, long delaySeconds) {

        redisRepository.setPendingFlag(eventId, delaySeconds);

        // 이벤트 ID를 스케줄링 ZSET에 추가
        redisRepository.addToSchedule(eventId, delaySeconds);

        log.info("이벤트 예약됨: eventId={}, delay={}s", eventId, delaySeconds);

    }

    @Override
    public void markEventAsOpened(String eventId) {
        redisRepository.markAsOpen(eventId);
        log.info("이벤트 오픈 상태로 갱신됨: eventId={}", eventId);
    }

    @Override
    public boolean isAlreadyOpened(String eventId) {
        return redisRepository.isAlreadyOpened(eventId);
    }

    /**
     * 만료된 대기 이벤트 ID 목록 조회 (ZSET 기반)
     *
     * @return 만료된 대기 이벤트 ID 목록
     */
    @Override
    public List<String> getExpiredPendingEvents() {
        return redisRepository.getExpiredPendingEvents();
    }

    @Override
    public void removeFromSchedule(String eventId) {
        redisRepository.removeFromSchedule(eventId);
    }

    @Override
    public boolean acquireEventLock(String eventId, Duration timeout) {
        return redisRepository.acquireEventLock(eventId, timeout);
    }

    @Override
    public void releaseEventLock(String eventId) {
        redisRepository.releaseEventLock(eventId);
    }
}
