package com.chae.promo.event.service;

/*
 * Tests covered:
 *   - scheduleEvent()         : Redis ZSET 스케줄링 성공 / 저장소 예외 전파
 *   - markEventAsOpened()     : 신규 이벤트 오픈 성공 / 이미 처리된 이벤트 중복 방지 /
 *                               redisRepository.markAsOpen 예외 발생 후에도 아웃박스 저장 진행
 *   - isAlreadyOpened()       : 이미 열린 이벤트 true 반환 / 미열린 이벤트 false 반환
 *   - getExpiredPendingEvents(): 만료된 이벤트 목록 반환 / 빈 목록 반환
 *   - removeFromSchedule()    : 저장소 removeFromSchedule 위임 검증
 *   - acquireEventLock()      : 락 획득 성공 true / 이미 잠긴 경우 false
 *   - releaseEventLock()      : 저장소 releaseEventLock 위임 검증
 */

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.event.domain.EventOpenPayload;
import com.chae.promo.event.redis.EventRedisRepository;
import com.chae.promo.outbox.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventServiceImpl 단위 테스트")
class EventServiceTest {

    @Mock
    private EventRedisRepository redisRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private EventServiceImpl eventService;

    private static final String EVENT_ID = "event-001";

    // -------------------------------------------------------------------------
    // scheduleEvent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("scheduleEvent: setPendingFlag, addToSchedule 가 올바른 인자로 호출된다")
    void scheduleEvent_callsRepositoryWithCorrectArguments() {
        // Arrange
        long delaySeconds = 300L;

        // Act
        eventService.scheduleEvent(EVENT_ID, delaySeconds);

        // Assert
        verify(redisRepository, times(1)).setPendingFlag(EVENT_ID, delaySeconds);
        verify(redisRepository, times(1)).addToSchedule(EVENT_ID, delaySeconds);
    }

    @Test
    @DisplayName("scheduleEvent: setPendingFlag 가 예외를 던지면 예외가 호출자에게 전파된다")
    void scheduleEvent_propagatesExceptionFromSetPendingFlag() {
        // Arrange
        long delaySeconds = 60L;
        doThrow(new RuntimeException("Redis 오류")).when(redisRepository).setPendingFlag(EVENT_ID, delaySeconds);

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventService.scheduleEvent(EVENT_ID, delaySeconds)
        ).isInstanceOf(RuntimeException.class);

        verify(redisRepository, times(1)).setPendingFlag(EVENT_ID, delaySeconds);
        // setPendingFlag 가 실패하면 addToSchedule 은 호출되지 않아야 한다
        verify(redisRepository, never()).addToSchedule(any(), anyLong());
    }

    @Test
    @DisplayName("scheduleEvent: 지연 시간 0초로 즉시 스케줄링할 수 있다")
    void scheduleEvent_withZeroDelaySeconds() {
        // Arrange & Act
        eventService.scheduleEvent(EVENT_ID, 0L);

        // Assert
        verify(redisRepository).setPendingFlag(EVENT_ID, 0L);
        verify(redisRepository).addToSchedule(EVENT_ID, 0L);
    }

    // -------------------------------------------------------------------------
    // markEventAsOpened
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markEventAsOpened: 신규 이벤트 — markAsOpen, outboxService.saveEvent 가 모두 호출된다")
    void markEventAsOpened_newEvent_callsMarkAsOpenAndSavesOutbox() {
        // Arrange
        when(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID)).thenReturn(false);

        // Act
        eventService.markEventAsOpened(EVENT_ID);

        // Assert
        verify(redisRepository, times(1)).markAsOpen(EVENT_ID);
        verify(outboxService, times(1)).existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID);
        verify(outboxService, times(1)).saveEvent(any(String.class), eq(TopicNames.EVENT_OPEN), eq(EVENT_ID), any(EventOpenPayload.class));
    }

    @Test
    @DisplayName("markEventAsOpened: outboxService.saveEvent 에 전달된 payload 필드가 올바르게 설정된다")
    void markEventAsOpened_newEvent_outboxPayloadHasCorrectFields() {
        // Arrange
        when(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID)).thenReturn(false);

        ArgumentCaptor<EventOpenPayload> payloadCaptor = ArgumentCaptor.forClass(EventOpenPayload.class);

        // Act
        eventService.markEventAsOpened(EVENT_ID);

        // Assert
        verify(outboxService).saveEvent(any(), any(), any(), payloadCaptor.capture());

        EventOpenPayload capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload.getEventDomainId()).isEqualTo(EVENT_ID);
        assertThat(capturedPayload.getStatus()).isEqualTo("EVENT_OPEN");
        assertThat(capturedPayload.getOutboxEventId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("markEventAsOpened: outboxService.saveEvent 의 outboxEventId 인자가 payload 의 outboxEventId 와 일치한다")
    void markEventAsOpened_newEvent_outboxEventIdMatchesPayload() {
        // Arrange
        when(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID)).thenReturn(false);

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventOpenPayload> payloadCaptor = ArgumentCaptor.forClass(EventOpenPayload.class);

        // Act
        eventService.markEventAsOpened(EVENT_ID);

        // Assert
        verify(outboxService).saveEvent(eventIdCaptor.capture(), any(), any(), payloadCaptor.capture());

        assertThat(eventIdCaptor.getValue()).isEqualTo(payloadCaptor.getValue().getOutboxEventId());
    }

    @Test
    @DisplayName("markEventAsOpened: 이미 처리된 이벤트 — outboxService.saveEvent 가 호출되지 않는다")
    void markEventAsOpened_alreadyProcessed_doesNotSaveOutbox() {
        // Arrange
        when(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID)).thenReturn(true);

        // Act
        eventService.markEventAsOpened(EVENT_ID);

        // Assert
        verify(redisRepository, times(1)).markAsOpen(EVENT_ID);
        verify(outboxService, times(1)).existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID);
        verify(outboxService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("markEventAsOpened: redisRepository.markAsOpen 이 예외를 던져도 아웃박스 저장이 계속 진행된다")
    void markEventAsOpened_markAsOpenThrows_continuesWithOutboxSave() {
        // Arrange — markAsOpen 예외 발생 (catch 블록에서 삼켜짐)
        doThrow(new RuntimeException("Redis 오류")).when(redisRepository).markAsOpen(EVENT_ID);
        when(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID)).thenReturn(false);

        // Act — 예외가 전파되지 않아야 한다
        assertThatCode(() -> eventService.markEventAsOpened(EVENT_ID)).doesNotThrowAnyException();

        // Assert — 아웃박스는 여전히 저장되어야 한다
        verify(outboxService, times(1)).saveEvent(any(), eq(TopicNames.EVENT_OPEN), eq(EVENT_ID), any());
    }

    @Test
    @DisplayName("markEventAsOpened: redisRepository.markAsOpen 예외 + 이미 처리된 이벤트 — outboxService.saveEvent 는 호출되지 않는다")
    void markEventAsOpened_markAsOpenThrows_andAlreadyExists_doesNotSaveOutbox() {
        // Arrange
        doThrow(new RuntimeException("Redis 오류")).when(redisRepository).markAsOpen(EVENT_ID);
        when(outboxService.existsByTypeAndAggregateId(TopicNames.EVENT_OPEN, EVENT_ID)).thenReturn(true);

        // Act
        assertThatCode(() -> eventService.markEventAsOpened(EVENT_ID)).doesNotThrowAnyException();

        // Assert
        verify(outboxService, never()).saveEvent(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // isAlreadyOpened
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isAlreadyOpened: 저장소가 true 를 반환하면 true 를 반환한다")
    void isAlreadyOpened_returnsTrue_whenRepositoryReturnsTrue() {
        // Arrange
        when(redisRepository.isAlreadyOpened(EVENT_ID)).thenReturn(true);

        // Act
        boolean result = eventService.isAlreadyOpened(EVENT_ID);

        // Assert
        assertThat(result).isTrue();
        verify(redisRepository, times(1)).isAlreadyOpened(EVENT_ID);
    }

    @Test
    @DisplayName("isAlreadyOpened: 저장소가 false 를 반환하면 false 를 반환한다")
    void isAlreadyOpened_returnsFalse_whenRepositoryReturnsFalse() {
        // Arrange
        when(redisRepository.isAlreadyOpened(EVENT_ID)).thenReturn(false);

        // Act
        boolean result = eventService.isAlreadyOpened(EVENT_ID);

        // Assert
        assertThat(result).isFalse();
        verify(redisRepository, times(1)).isAlreadyOpened(EVENT_ID);
    }

    @Test
    @DisplayName("isAlreadyOpened: 저장소 예외가 호출자에게 전파된다")
    void isAlreadyOpened_propagatesRepositoryException() {
        // Arrange
        when(redisRepository.isAlreadyOpened(EVENT_ID)).thenThrow(new RuntimeException("Redis 조회 실패"));

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventService.isAlreadyOpened(EVENT_ID)
        ).isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // getExpiredPendingEvents
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getExpiredPendingEvents: 만료된 이벤트 목록을 그대로 반환한다")
    void getExpiredPendingEvents_returnsListFromRepository() {
        // Arrange
        List<String> expiredEvents = List.of("event-100", "event-200", "event-300");
        when(redisRepository.getExpiredPendingEvents()).thenReturn(expiredEvents);

        // Act
        List<String> result = eventService.getExpiredPendingEvents();

        // Assert
        assertThat(result).containsExactlyElementsOf(expiredEvents);
        verify(redisRepository, times(1)).getExpiredPendingEvents();
    }

    @Test
    @DisplayName("getExpiredPendingEvents: 저장소가 빈 목록을 반환하면 빈 목록을 반환한다")
    void getExpiredPendingEvents_returnsEmptyList_whenNoExpiredEvents() {
        // Arrange
        when(redisRepository.getExpiredPendingEvents()).thenReturn(Collections.emptyList());

        // Act
        List<String> result = eventService.getExpiredPendingEvents();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getExpiredPendingEvents: 단일 이벤트를 포함한 목록을 반환한다")
    void getExpiredPendingEvents_returnsSingleElementList() {
        // Arrange
        when(redisRepository.getExpiredPendingEvents()).thenReturn(List.of(EVENT_ID));

        // Act
        List<String> result = eventService.getExpiredPendingEvents();

        // Assert
        assertThat(result).hasSize(1).containsExactly(EVENT_ID);
    }

    @Test
    @DisplayName("getExpiredPendingEvents: 저장소 예외가 호출자에게 전파된다")
    void getExpiredPendingEvents_propagatesRepositoryException() {
        // Arrange
        when(redisRepository.getExpiredPendingEvents()).thenThrow(new RuntimeException("Redis 오류"));

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventService.getExpiredPendingEvents()
        ).isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // removeFromSchedule
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("removeFromSchedule: 저장소의 removeFromSchedule 가 올바른 eventId 로 호출된다")
    void removeFromSchedule_delegatesToRepository() {
        // Arrange & Act
        eventService.removeFromSchedule(EVENT_ID);

        // Assert
        verify(redisRepository, times(1)).removeFromSchedule(EVENT_ID);
    }

    @Test
    @DisplayName("removeFromSchedule: 저장소 예외가 호출자에게 전파된다")
    void removeFromSchedule_propagatesRepositoryException() {
        // Arrange
        doThrow(new RuntimeException("Redis 오류")).when(redisRepository).removeFromSchedule(EVENT_ID);

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventService.removeFromSchedule(EVENT_ID)
        ).isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // acquireEventLock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquireEventLock: 저장소가 true 를 반환하면 락 획득 성공으로 true 를 반환한다")
    void acquireEventLock_returnsTrue_whenLockAcquired() {
        // Arrange
        Duration timeout = Duration.ofSeconds(30);
        when(redisRepository.acquireEventLock(EVENT_ID, timeout)).thenReturn(true);

        // Act
        boolean result = eventService.acquireEventLock(EVENT_ID, timeout);

        // Assert
        assertThat(result).isTrue();
        verify(redisRepository, times(1)).acquireEventLock(EVENT_ID, timeout);
    }

    @Test
    @DisplayName("acquireEventLock: 저장소가 false 를 반환하면 락 획득 실패로 false 를 반환한다")
    void acquireEventLock_returnsFalse_whenLockAlreadyHeld() {
        // Arrange
        Duration timeout = Duration.ofSeconds(30);
        when(redisRepository.acquireEventLock(EVENT_ID, timeout)).thenReturn(false);

        // Act
        boolean result = eventService.acquireEventLock(EVENT_ID, timeout);

        // Assert
        assertThat(result).isFalse();
        verify(redisRepository, times(1)).acquireEventLock(EVENT_ID, timeout);
    }

    @Test
    @DisplayName("acquireEventLock: 타임아웃이 0 Duration 이어도 저장소에 그대로 위임된다")
    void acquireEventLock_withZeroDurationTimeout() {
        // Arrange
        Duration zeroDuration = Duration.ZERO;
        when(redisRepository.acquireEventLock(EVENT_ID, zeroDuration)).thenReturn(false);

        // Act
        boolean result = eventService.acquireEventLock(EVENT_ID, zeroDuration);

        // Assert
        assertThat(result).isFalse();
        verify(redisRepository).acquireEventLock(EVENT_ID, zeroDuration);
    }

    @Test
    @DisplayName("acquireEventLock: 저장소 예외가 호출자에게 전파된다")
    void acquireEventLock_propagatesRepositoryException() {
        // Arrange
        Duration timeout = Duration.ofSeconds(10);
        when(redisRepository.acquireEventLock(EVENT_ID, timeout)).thenThrow(new RuntimeException("Redis 오류"));

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventService.acquireEventLock(EVENT_ID, timeout)
        ).isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // releaseEventLock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("releaseEventLock: 저장소의 releaseEventLock 가 올바른 eventId 로 호출된다")
    void releaseEventLock_delegatesToRepository() {
        // Arrange & Act
        eventService.releaseEventLock(EVENT_ID);

        // Assert
        verify(redisRepository, times(1)).releaseEventLock(EVENT_ID);
    }

    @Test
    @DisplayName("releaseEventLock: 저장소가 예외를 던지더라도 호출자에게 전파된다")
    void releaseEventLock_propagatesRepositoryException() {
        // Arrange
        doThrow(new RuntimeException("Redis 오류")).when(redisRepository).releaseEventLock(EVENT_ID);

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventService.releaseEventLock(EVENT_ID)
        ).isInstanceOf(RuntimeException.class);
    }
}
