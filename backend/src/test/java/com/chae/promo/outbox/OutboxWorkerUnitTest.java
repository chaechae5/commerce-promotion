package com.chae.promo.outbox;

import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.chae.promo.outbox.service.DomainOutboxPublisher;
import com.chae.promo.outbox.service.OutboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class OutboxWorkerUnitTest {
    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private DomainOutboxPublisher orderPlacedPublisher;

    @Mock
    private DomainOutboxPublisher eventOpenPublisher;

    @Mock
    private Clock clock;

    private OutboxWorker outboxWorker;

    @BeforeEach
    void setUp() {
        clock = Clock.systemUTC();
        List<DomainOutboxPublisher> publishers = List.of(orderPlacedPublisher, eventOpenPublisher);
        outboxWorker = new OutboxWorker(eventOutboxRepository, clock, publishers);
    }


    @Test
    @DisplayName("supports() 결과에 따라 올바른 퍼블리셔만 호출된다")
    void publishBatch_routesToCorrectPublisherBasedOnType() throws Exception {
        // given
        EventOutbox orderPlacedOutbox = EventOutbox.builder()
                .eventId("order-placed-event-id")
                .aggregateId("agg-1")
                .type("order.placed")
                .status(EventOutbox.Status.PENDING)
                .payloadJson("{}")
                .build();

        EventOutbox eventOpenOutbox = EventOutbox.builder()
                .eventId("event-open-event-id")
                .aggregateId("agg-2")
                .type("event.open")
                .status(EventOutbox.Status.PENDING)
                .payloadJson("{}")
                .build();

        given(eventOutboxRepository.lockAndFetch(any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(orderPlacedOutbox, eventOpenOutbox));

        // supports 매칭
        given(orderPlacedPublisher.supports("order.placed")).willReturn(true);
        given(orderPlacedPublisher.supports("event.open")).willReturn(false);

        given(eventOpenPublisher.supports("event.open")).willReturn(true);

        // when
        outboxWorker.publishBatch();

        // then
        then(orderPlacedPublisher).should(times(1)).publish("{}");
        then(eventOpenPublisher).should(times(1)).publish("{}");

        // 반대로 호출되지 않았는지도 확인
        then(orderPlacedPublisher).should(never()).publish("{\"wrong\":\"eventOpen\"}");
        then(eventOpenPublisher).should(never()).publish("{\"wrong\":\"orderPlaced\"}");

        assertThat(orderPlacedOutbox.getStatus()).isEqualTo(EventOutbox.Status.SENT);
        assertThat(eventOpenOutbox.getStatus()).isEqualTo(EventOutbox.Status.SENT);
    }
}
