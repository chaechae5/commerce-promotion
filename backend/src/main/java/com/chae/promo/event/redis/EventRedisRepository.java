package com.chae.promo.event.redis;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class EventRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final EventRedisKeyManager keyManager;

    // ------------ 이벤트 락 관련 메서드 ------------


    /**
     * 이벤트 락 획득
     * @param eventId 이벤트 ID
     * @param timeout 락 만료 시간
     * @return 락 획득 성공 여부
     */
    public boolean acquireEventLock(String eventId, Duration timeout) {
        try {
            String lockKey = keyManager.getEventLockKey(eventId);
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", timeout)
            );
        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 락 획득 실패: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }


    /**
     * 이벤트 락 해제
     * @param eventId 이벤트 ID
     */
    public void releaseEventLock(String eventId) {
        try {
            String lockKey = keyManager.getEventLockKey(eventId);
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("락 해제 실패: eventId={}, cause={}", eventId, e.getMessage());
        }
    }

    // -------------- 이벤트 스케줄 관련 메서드 --------------


    /**
     * 이벤트 시작 플래그를 PENDING으로 설정하고 TTL 예약
     *
     * @param eventId      이벤트 ID
     * @param delaySeconds 지연 시간 (초)
     */
    public void setPendingFlag(String eventId, long delaySeconds) {
        String key = null;
        try {
            key = keyManager.getEventStartFlagKey(eventId);
            redisTemplate.opsForValue().set(key, "PENDING", Duration.ofSeconds(delaySeconds));
        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);
        } catch (RedisSystemException e) {
            log.error("Redis TTL 예약 실패: key={}, delay={}", key, delaySeconds, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }

    /**
     * 이벤트를 스케줄링 ZSET에 추가
     *
     * @param eventId      이벤트 ID
     * @param delaySeconds 지연 시간 (초)
     */
    public void addToSchedule(String eventId, long delaySeconds) {
        String scheduleKey = null;
        try {
            scheduleKey = keyManager.getEventScheduleKey();
            long executeAt = System.currentTimeMillis() / 1000 + delaySeconds; // 현재 시간 + 지연 시간 (초)

            // ZSET에 이벤트 추가: score = 실행 시각
            redisTemplate.opsForZSet().add(scheduleKey, eventId, executeAt);

        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis ZSET 추가 실패: eventId={}, key={}, delay={}", eventId, scheduleKey, delaySeconds, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }

    /**
     * 이벤트 상태를 OPEN으로 갱신
     *
     * @param eventId 이벤트 ID
     */
    public void markAsOpen(String eventId) {
        String key = null;
        try {
            key = keyManager.getEventStatusKey(eventId);
            redisTemplate.opsForValue().set(key, "OPEN");

        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);
        } catch (RedisSystemException e) {
            log.error("Redis 상태 갱신 실패: eventId={}, key={}", eventId, key, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }

    /**
     * 이벤트가 이미 OPEN 상태인지 확인
     *
     * @param eventId 이벤트 ID
     * @return OPEN 상태 여부
     */
    public boolean isAlreadyOpened(String eventId) {
        String key = null;
        try {
            key = keyManager.getEventStatusKey(eventId);
            String status = redisTemplate.opsForValue().get(key);
            return "OPEN".equals(status);
        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 이벤트 ID: eventId={}", eventId, e);
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 상태 조회 실패: eventId={}, key={}", eventId, key, e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }


    /**
     * 만료된(실행 시각이 지난) 대기 중인 이벤트 목록 조회
     *
     * @return 만료된 이벤트 ID 목록
     */
    public List<String> getExpiredPendingEvents() {
        try {
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

        } catch (IllegalArgumentException e) {
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 작업 실패: cause={}", e.getMessage(), e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }

    /**
     * 이벤트를 스케줄에서 제거
     *
     * @param eventId 이벤트 ID
     */
    public void removeFromSchedule(String eventId) {
        try {
            String scheduleKey = keyManager.getEventScheduleKey();
            redisTemplate.opsForZSet().remove(scheduleKey, eventId);
            log.info("이벤트 스케줄에서 제거됨: eventId={}", eventId);

        }  catch (IllegalArgumentException e) {
            throw new CommonCustomException(CommonErrorCode.INVALID_EVENT_ID);

        } catch (RedisSystemException e) {
            log.error("Redis 작업 실패: eventId={}, cause={}", eventId, e.getMessage(), e);
            throw new CommonCustomException(CommonErrorCode.REDIS_OPERATION_FAILED);
        }
    }
}
