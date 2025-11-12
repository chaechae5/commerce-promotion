package com.chae.promo.event.redis;

import com.chae.promo.event.kafka.EventKafkaPublisher;
import com.chae.promo.event.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisKeyExpirationListenerTest {
    @Mock
    private EventService eventService;

    @Mock
    private EventRedisKeyManager keyManager;

    @Mock
    private EventKafkaPublisher eventKafkaPublisher;

    @InjectMocks
    private RedisKeyExpirationListener listener;


    private Message message(String body) {
        return new DefaultMessage(
                "__keyevent@0__:expired".getBytes(StandardCharsets.UTF_8), // dummy channel
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @DisplayName("이벤트 start_flag 형식이 아닌 키는 무시되어야 한다")
    void testIgnoreNonEventKey() {
        Message message = message("other:123:expired");

        when(keyManager.isEventStartFlagKey("other:123:expired")).thenReturn(false);

        listener.onMessage(message, null);

        //아무 동작도 수행되지 않아야 함 - mock의 어떤 메서드도 호출되지 않았음을 검증
        verifyNoInteractions(eventService);
        verifyNoInteractions(eventKafkaPublisher);
    }

    @Test
    @DisplayName("락 획득 실패 시 이벤트 처리 스킵")
    void testLockAcquireFails() {
        Message message = message("event:{E123}:start_flag");

        when(keyManager.isEventStartFlagKey(anyString())).thenReturn(true);
        when(keyManager.extractEventId(anyString())).thenReturn("E123");
        when(eventService.acquireEventLock(eq("E123"), any())).thenReturn(false);

        listener.onMessage(message, null);

        //호출되지 않음 - 특정 메서드가 호출되지 않았음을 검증
        verify(eventService, never()).markEventAsOpened(any());
    }

    @Test
    @DisplayName("이미 OPEN 상태이면 이벤트 처리 스킵")
    void testAlreadyOpened() {
        Message message = message("event:{E123}:start_flag");

        when(keyManager.isEventStartFlagKey(anyString())).thenReturn(true);
        when(keyManager.extractEventId(anyString())).thenReturn("E123");
        when(eventService.acquireEventLock(eq("E123"), any())).thenReturn(true); //락획득
        when(eventService.isAlreadyOpened("E123")).thenReturn(true); // 이미 OPEN 상태

        listener.onMessage(message, null);

        verify(eventService).releaseEventLock("E123"); // 락은 항상 해제되어야 함
        verify(eventService, never()).markEventAsOpened(any());
    }

    @Test
    @DisplayName("정상적인 TTL 만료 이벤트 시 mark → publish → remove 순서 호출")
    void testSuccessfulEventHandling() {
        Message message = message("event:{E123}:start_flag");

        when(keyManager.isEventStartFlagKey(anyString())).thenReturn(true);
        when(keyManager.extractEventId(anyString())).thenReturn("E123");
        when(eventService.acquireEventLock(eq("E123"), any())).thenReturn(true);
        when(eventService.isAlreadyOpened("E123")).thenReturn(false);

        listener.onMessage(message, null);

        //호출 순서 검증
        InOrder inOrder = inOrder(eventService, eventKafkaPublisher);
        inOrder.verify(eventService).markEventAsOpened("E123");
        inOrder.verify(eventService).removeFromSchedule("E123");

        //락 해제 보장
        verify(eventService).releaseEventLock("E123");
    }

    @Test
    @DisplayName("이벤트 처리 중 예외 발생 시에도 락은 해제되어야 한다")
    void testLockAlwaysReleasedOnException() {
        Message message = message("event:{E123}:start_flag");

        when(keyManager.isEventStartFlagKey(anyString())).thenReturn(true);
        when(keyManager.extractEventId(anyString())).thenReturn("E123");
        when(eventService.acquireEventLock(eq("E123"), any())).thenReturn(true);
        when(eventService.isAlreadyOpened("E123")).thenReturn(false);

        doThrow(new RuntimeException("Redis error"))
                .when(eventService).markEventAsOpened("E123");

        listener.onMessage(message, null);

        //락 해제 보장
        verify(eventService).releaseEventLock("E123");
    }
}
