package com.chae.promo.event.service;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.event.domain.EventOpenPayload;
import com.chae.promo.event.redis.EventRedisRepository;
import com.chae.promo.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRedisRepository redisRepository;
    private final OutboxService outboxService;

    @Override
    public void scheduleEvent(String eventId, long delaySeconds) {

        redisRepository.setPendingFlag(eventId, delaySeconds);

        // 이벤트 ID를 스케줄링 ZSET에 추가
        redisRepository.addToSchedule(eventId, delaySeconds);

        log.info("이벤트 예약됨: eventId={}, delay={}s", eventId, delaySeconds);

    }

    @Override
    @Transactional
    public void markEventAsOpened(String eventId) {
        try{
            redisRepository.markAsOpen(eventId);
        }catch (Exception e) {
            log.error("이벤트 오픈 중 오류 발생: eventId={}", eventId, e);
        }

        // 중복 처리 방지
        if(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, eventId)) {
            log.info("이벤트 오픈 이미 처리됨: eventId={}", eventId);
            return;
        }

        String outboxEventId = UuidUtil.generate();

        EventOpenPayload payload = EventOpenPayload.builder()
                .outboxEventId(outboxEventId)
                .eventDomainId(eventId)
                .status("EVENT_OPEN")
                .build();

        outboxService.saveEvent(outboxEventId, TopicNames.EVENT_OPEN, eventId, payload);

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
