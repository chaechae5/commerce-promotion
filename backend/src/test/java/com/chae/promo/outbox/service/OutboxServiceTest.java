/*
 * Tests for OutboxServiceImpl
 *
 * Covered methods:
 *   - saveEvent(String eventId, String type, String aggregateId, Object payload)
 *       1. 정상 저장 - ObjectMapper 직렬화 후 PENDING 상태 EventOutbox를 repository에 저장
 *       2. payload 직렬화 검증 - 저장된 EventOutbox의 payloadJson 필드가 올바른 JSON인지 확인
 *       3. 초기 필드값 검증 - status=PENDING, retryCount=0, nextRetryAt=LocalDateTime.now(clock)
 *       4. ObjectMapper 직렬화 실패 시 RuntimeException("Outbox 저장 실패") 래핑하여 재던짐
 *       5. repository.save() 실패 시 RuntimeException("Outbox 저장 실패") 래핑하여 재던짐
 *   - existsByTypeAndAggregateId(String type, String aggregateId)
 *       1. 이벤트 존재 시 true 반환
 *       2. 이벤트 없을 때 false 반환
 */
package com.chae.promo.outbox.service;

import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxServiceImpl 단위 테스트")
class OutboxServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private OutboxServiceImpl outboxService;

    // ────────────────────────────────────────────────────────────────────────────
    // saveEvent
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvent: 정상 저장 - repository.save()가 한 번 호출된다")
    void saveEvent_success_repositorySaveCalledOnce() throws Exception {
        // given
        String eventId     = "evt-001";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-001";
        Object payload     = new TestPayload("item-1", 2);

        given(objectMapper.writeValueAsString(payload)).willReturn("{\"name\":\"item-1\",\"quantity\":2}");
        stubFixedClock();

        // when
        outboxService.saveEvent(eventId, type, aggregateId, payload);

        // then
        then(eventOutboxRepository).should().save(any(EventOutbox.class));
    }

    @Test
    @DisplayName("saveEvent: 저장된 EventOutbox의 eventId, type, aggregateId가 파라미터와 일치한다")
    void saveEvent_success_savedEntityHasCorrectIdentifiers() throws Exception {
        // given
        String eventId     = "evt-002";
        String type        = "EVENT_OPEN";
        String aggregateId = "promo-agg-002";
        Object payload     = new TestPayload("promo", 1);
        String expectedJson = "{\"name\":\"promo\",\"quantity\":1}";

        given(objectMapper.writeValueAsString(payload)).willReturn(expectedJson);
        stubFixedClock();

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);

        // when
        outboxService.saveEvent(eventId, type, aggregateId, payload);

        // then
        then(eventOutboxRepository).should().save(captor.capture());
        EventOutbox saved = captor.getValue();

        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
    }

    @Test
    @DisplayName("saveEvent: payloadJson 필드가 ObjectMapper가 반환한 JSON 문자열과 동일하다")
    void saveEvent_success_payloadJsonIsSerializedCorrectly() throws Exception {
        // given
        String eventId     = "evt-003";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-003";
        Object payload     = new TestPayload("book", 3);
        String expectedJson = "{\"name\":\"book\",\"quantity\":3}";

        given(objectMapper.writeValueAsString(payload)).willReturn(expectedJson);
        stubFixedClock();

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);

        // when
        outboxService.saveEvent(eventId, type, aggregateId, payload);

        // then
        then(eventOutboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).isEqualTo(expectedJson);
    }

    @Test
    @DisplayName("saveEvent: 저장된 EventOutbox의 초기 상태는 PENDING이다")
    void saveEvent_success_initialStatusIsPending() throws Exception {
        // given
        String eventId     = "evt-004";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-004";
        Object payload     = new TestPayload("pen", 5);

        given(objectMapper.writeValueAsString(payload)).willReturn("{\"name\":\"pen\",\"quantity\":5}");
        stubFixedClock();

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);

        // when
        outboxService.saveEvent(eventId, type, aggregateId, payload);

        // then
        then(eventOutboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
    }

    @Test
    @DisplayName("saveEvent: 저장된 EventOutbox의 초기 retryCount는 0이다")
    void saveEvent_success_initialRetryCountIsZero() throws Exception {
        // given
        String eventId     = "evt-005";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-005";
        Object payload     = new TestPayload("cup", 1);

        given(objectMapper.writeValueAsString(payload)).willReturn("{\"name\":\"cup\",\"quantity\":1}");
        stubFixedClock();

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);

        // when
        outboxService.saveEvent(eventId, type, aggregateId, payload);

        // then
        then(eventOutboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getRetryCount()).isZero();
    }

    @Test
    @DisplayName("saveEvent: nextRetryAt이 Clock 기반 현재 시간으로 설정된다")
    void saveEvent_success_nextRetryAtIsSetFromClock() throws Exception {
        // given
        String eventId     = "evt-006";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-006";
        Object payload     = new TestPayload("desk", 1);

        given(objectMapper.writeValueAsString(payload)).willReturn("{\"name\":\"desk\",\"quantity\":1}");

        Instant fixedInstant = Instant.parse("2026-03-27T09:00:00Z");
        ZoneId zone = ZoneId.of("UTC");
        given(clock.instant()).willReturn(fixedInstant);
        given(clock.getZone()).willReturn(zone);

        LocalDateTime expectedNextRetryAt = LocalDateTime.now(Clock.fixed(fixedInstant, zone));

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);

        // when
        outboxService.saveEvent(eventId, type, aggregateId, payload);

        // then
        then(eventOutboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getNextRetryAt()).isEqualTo(expectedNextRetryAt);
    }

    @Test
    @DisplayName("saveEvent: ObjectMapper 직렬화 실패 시 RuntimeException(\"Outbox 저장 실패\")이 던져진다")
    void saveEvent_serializationFailure_throwsRuntimeException() throws Exception {
        // given
        String eventId     = "evt-007";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-007";
        Object payload     = new Object();

        given(objectMapper.writeValueAsString(payload))
                .willThrow(new JsonProcessingException("serialization error") {});

        // when & then
        assertThatThrownBy(() -> outboxService.saveEvent(eventId, type, aggregateId, payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Outbox 저장 실패")
                .hasCauseInstanceOf(JsonProcessingException.class);

        then(eventOutboxRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("saveEvent: repository.save() 실패 시 RuntimeException(\"Outbox 저장 실패\")이 던져진다")
    void saveEvent_repositorySaveFailure_throwsRuntimeException() throws Exception {
        // given
        String eventId     = "evt-008";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-008";
        Object payload     = new TestPayload("lamp", 2);

        given(objectMapper.writeValueAsString(payload)).willReturn("{\"name\":\"lamp\",\"quantity\":2}");
        stubFixedClock();
        given(eventOutboxRepository.save(any(EventOutbox.class)))
                .willThrow(new RuntimeException("DB connection lost"));

        // when & then
        assertThatThrownBy(() -> outboxService.saveEvent(eventId, type, aggregateId, payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Outbox 저장 실패")
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("DB connection lost");
    }

    @Test
    @DisplayName("saveEvent: null payload는 ObjectMapper에 위임되어 'null' 문자열로 직렬화된다")
    void saveEvent_nullPayload_serializesAsNullLiteral() throws Exception {
        // given
        String eventId     = "evt-009";
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-009";

        given(objectMapper.writeValueAsString(null)).willReturn("null");
        stubFixedClock();

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);

        // when
        outboxService.saveEvent(eventId, type, aggregateId, null);

        // then
        then(eventOutboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).isEqualTo("null");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // existsByTypeAndAggregateId
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByTypeAndAggregateId: 이벤트가 존재할 때 true를 반환한다")
    void existsByTypeAndAggregateId_eventExists_returnsTrue() {
        // given
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-100";

        given(eventOutboxRepository.existsByTypeAndAggregateId(type, aggregateId)).willReturn(true);

        // when
        boolean result = outboxService.existsByTypeAndAggregateId(type, aggregateId);

        // then
        assertThat(result).isTrue();
        then(eventOutboxRepository).should().existsByTypeAndAggregateId(type, aggregateId);
    }

    @Test
    @DisplayName("existsByTypeAndAggregateId: 이벤트가 존재하지 않을 때 false를 반환한다")
    void existsByTypeAndAggregateId_eventNotExists_returnsFalse() {
        // given
        String type        = "ORDER_PLACED";
        String aggregateId = "order-agg-999";

        given(eventOutboxRepository.existsByTypeAndAggregateId(type, aggregateId)).willReturn(false);

        // when
        boolean result = outboxService.existsByTypeAndAggregateId(type, aggregateId);

        // then
        assertThat(result).isFalse();
        then(eventOutboxRepository).should().existsByTypeAndAggregateId(type, aggregateId);
    }

    @Test
    @DisplayName("existsByTypeAndAggregateId: type이 다르면 같은 aggregateId라도 false를 반환한다")
    void existsByTypeAndAggregateId_differentType_returnsFalse() {
        // given
        String aggregateId   = "shared-agg-001";
        String existingType  = "ORDER_PLACED";
        String queryType     = "EVENT_OPEN";

        given(eventOutboxRepository.existsByTypeAndAggregateId(existingType, aggregateId)).willReturn(true);
        given(eventOutboxRepository.existsByTypeAndAggregateId(queryType, aggregateId)).willReturn(false);

        // when
        boolean existingResult = outboxService.existsByTypeAndAggregateId(existingType, aggregateId);
        boolean queryResult    = outboxService.existsByTypeAndAggregateId(queryType, aggregateId);

        // then
        assertThat(existingResult).isTrue();
        assertThat(queryResult).isFalse();
    }

    @Test
    @DisplayName("existsByTypeAndAggregateId: repository에 정확한 파라미터가 전달된다")
    void existsByTypeAndAggregateId_correctArgumentsForwardedToRepository() {
        // given
        String type        = "EVENT_OPEN";
        String aggregateId = "promo-agg-200";

        given(eventOutboxRepository.existsByTypeAndAggregateId(type, aggregateId)).willReturn(true);

        ArgumentCaptor<String> typeCaptor        = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateIdCaptor = ArgumentCaptor.forClass(String.class);

        // when
        outboxService.existsByTypeAndAggregateId(type, aggregateId);

        // then
        then(eventOutboxRepository).should()
                .existsByTypeAndAggregateId(typeCaptor.capture(), aggregateIdCaptor.capture());

        assertThat(typeCaptor.getValue()).isEqualTo(type);
        assertThat(aggregateIdCaptor.getValue()).isEqualTo(aggregateId);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Clock mock을 고정된 시각(2026-03-27T00:00:00Z)으로 스텁한다.
     * LocalDateTime.now(clock) 호출이 결정론적인 값을 반환하도록 보장한다.
     */
    private void stubFixedClock() {
        Instant fixedInstant = Instant.parse("2026-03-27T00:00:00Z");
        given(clock.instant()).willReturn(fixedInstant);
        given(clock.getZone()).willReturn(ZoneId.of("UTC"));
    }

    /**
     * 직렬화 테스트에서 payload로 사용되는 간단한 내부 레코드.
     */
    record TestPayload(String name, int quantity) {}
}
