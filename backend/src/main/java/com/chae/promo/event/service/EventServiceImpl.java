package com.chae.promo.event.service;

import com.chae.promo.event.redis.EventRedisKeyManager;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
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

    private final EventRedisKeyManager keyManager;

    private final StringRedisTemplate redisTemplate;
    @Override
    public void scheduleEvent(String eventId, long delaySeconds) {

        try{
            String key = keyManager.getEventStartFlagKey(eventId);
            redisTemplate.opsForValue().set(key, "PENDING", Duration.ofSeconds(delaySeconds));

            // ZSET 스케줄 키 (ZSET 기반)
            String scheduleKey = keyManager.getEventScheduleKey();
            long executeAt = System.currentTimeMillis() / 1000 + delaySeconds; // 현재 시간 + 지연 시간 (초)

            // ZSET에 이벤트 추가: score = 실행 시각
            redisTemplate.opsForZSet().add(scheduleKey, eventId, executeAt);

            log.info("이벤트 예약됨: eventId={}, delay={}s", eventId, delaySeconds);

        } catch (IllegalArgumentException e){
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 작업 실패: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);

        } catch (Exception e) {
            log.error("이벤트 오픈 처리 중 알 수 없는 오류 발생: eventId={}, cause={}", eventId, e.getMessage(), e);
            throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public void markEventAsOpened(String eventId) {
        try {
            String key = keyManager.getEventStatusKey(eventId);
            redisTemplate.opsForValue().set(key, "OPEN");
            log.info("이벤트 오픈 상태로 갱신됨: eventId={}", eventId);

        } catch (IllegalArgumentException e){
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 작업 실패: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);

        } catch (Exception e) {
            log.error("이벤트 오픈 처리 중 알 수 없는 오류 발생: eventId={}, cause={}", eventId, e.getMessage(), e);
            throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean isAlreadyOpened(String eventId) {
        try{
            String status = keyManager.getEventStatusKey(eventId);
            return "OPEN".equals(status);

        } catch (IllegalArgumentException e){
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 작업 실패: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);

        } catch (Exception e) {
            log.error("이벤트 오픈 처리 중 알 수 없는 오류 발생: eventId={}, cause={}", eventId, e.getMessage(), e);
            throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * 만료된 대기 이벤트 ID 목록 조회 (ZSET 기반)
     * @return 만료된 대기 이벤트 ID 목록
     */
    @Override
    public List<String> getExpiredPendingEvents() {
        try{

            String scheduleKey = keyManager.getEventScheduleKey();
            long now = System.currentTimeMillis() / 1000;

            // 실행 시각(now) 이하의 이벤트 조회
            Set<String> expiredEventIds = redisTemplate.opsForZSet()
                    .rangeByScore(scheduleKey, 0, now);

            if (expiredEventIds == null || expiredEventIds.isEmpty()) {
                return Collections.emptyList();
            }

            log.info("만료된 이벤트 수={}, 목록={}", expiredEventIds.size(), expiredEventIds);

            return new ArrayList<>(expiredEventIds);

        }catch (RedisSystemException e){
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);

        } catch (Exception e){
            throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void removeFromSchedule(String eventId) {
        try{
            String scheduleKey = keyManager.getEventScheduleKey();
            redisTemplate.opsForZSet().remove(scheduleKey, eventId);
            log.info("이벤트 스케줄에서 제거됨: eventId={}", eventId);

        } catch (RedisSystemException e){
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);

        } catch (Exception e){
            throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean acquireEventLock(String eventId, Duration timeout) {
        try {
            String lockKey = keyManager.getEventLockKey(eventId);
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", timeout)
            );
        } catch (RedisSystemException e) {
            log.error("Redis 락 획득 실패: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }    }

    @Override
    public void releaseEventLock(String eventId) {
        try {
            String lockKey = keyManager.getEventLockKey(eventId);
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("락 해제 실패: eventId={}, cause={}", eventId, e.getMessage());
        }
    }
}
