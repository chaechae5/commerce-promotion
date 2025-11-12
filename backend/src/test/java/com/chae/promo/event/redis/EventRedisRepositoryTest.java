package com.chae.promo.event.redis;

import com.chae.promo.exception.CommonCustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventRedisRepositoryTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private EventRedisKeyManager keyManager;

    @InjectMocks
    private EventRedisRepository repository;

    @Test
    @DisplayName("유효하지 않은 이벤트 ID는 INVALID_EVENT_ID 예외 발생")
    void invalidEventId_shouldThrowCustomException() {
        when(keyManager.getEventLockKey(anyString()))
                .thenThrow(new IllegalArgumentException("Invalid"));

        assertThrows(CommonCustomException.class,
                () -> repository.acquireEventLock("bad_id", Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("RedisSystemException 발생 시 REDIS_OPERATION_FAILED 예외 발생")
    void redisError_shouldThrowCustomException() {
        when(keyManager.getEventLockKey(anyString())).thenReturn("event:{E123}:lock");
        when(redisTemplate.opsForValue()).thenThrow(new RedisSystemException("Redis down", null));

        assertThrows(CommonCustomException.class,
                () -> repository.acquireEventLock("E123", Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("정상 동작 시 락 획득 true 반환")
    void acquireLock_success() {
        var ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(keyManager.getEventLockKey(anyString())).thenReturn("event:{E123}:lock");

        boolean result = repository.acquireEventLock("E123", Duration.ofSeconds(10));

        assertTrue(result);
        verify(ops).setIfAbsent("event:{E123}:lock", "1", Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("정상 동작 시 pending 플래그 설정")
    void setPendingFlag_success() {
        var ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(keyManager.getEventStartFlagKey("E123")).thenReturn("event:{E123}:start_flag");

        repository.setPendingFlag("E123", 10);

        verify(ops).set("event:{E123}:start_flag", "PENDING", Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("정상 동작 시 스케줄에 이벤트 추가")
    void addToSchedule_success() {
        var zOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zOps);
        when(keyManager.getEventScheduleKey()).thenReturn("event:schedule");

        repository.addToSchedule("E123", 10);

        verify(zOps).add(eq("event:schedule"), eq("E123"), anyDouble());
    }

    @Test
    @DisplayName("정상 동작 시 OPEN 상태로 갱신")
    void markAsOpen_success() {
        var ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(keyManager.getEventStatusKey("E123")).thenReturn("event:{E123}:status");

        repository.markAsOpen("E123");

        verify(ops).set("event:{E123}:status", "OPEN");
    }

    @Test
    @DisplayName("상태가 OPEN이면 true 반환")
    void isAlreadyOpened_open() {
        var ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(keyManager.getEventStatusKey("E123")).thenReturn("event:{E123}:status");
        when(ops.get("event:{E123}:status")).thenReturn("OPEN");

        boolean result = repository.isAlreadyOpened("E123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("상태가 OPEN이 아니면 false 반환")
    void isAlreadyOpened_notOpen() {
        var ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(keyManager.getEventStatusKey("E123")).thenReturn("event:{E123}:status");
        when(ops.get("event:{E123}:status")).thenReturn("PENDING");

        boolean result = repository.isAlreadyOpened("E123");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("비어있으면 빈 리스트 반환")
    void getExpiredPendingEvents_empty() {
        var zOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zOps);
        when(keyManager.getEventScheduleKey()).thenReturn("event:schedule");
        when(zOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptySet());

        var result = repository.getExpiredPendingEvents();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("값이 있으면 목록 반환")
    void getExpiredPendingEvents_values() {
        var zOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zOps);
        when(keyManager.getEventScheduleKey()).thenReturn("event:schedule");
        when(zOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of("E1", "E2"));

        var result = repository.getExpiredPendingEvents();

        assertThat(result).containsExactlyInAnyOrder("E1", "E2");
    }

    @Test
    @DisplayName("정상 동작 시 remove 호출됨")
    void removeFromSchedule_success() {
        var zOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zOps);
        when(keyManager.getEventScheduleKey()).thenReturn("event:schedule");

        repository.removeFromSchedule("E123");

        verify(zOps).remove("event:schedule", "E123");
    }

}
