package com.chae.promo.outbox;

import com.chae.promo.order.entity.UserType;
import com.chae.promo.order.event.OrderPlacedEvent;
import com.chae.promo.order.event.OrderPlacedEventPublisher;
import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.chae.promo.outbox.service.OutboxWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class OutboxWorkerUnitTest {
    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private OrderPlacedEventPublisher orderPlacedEventPublisher;

    private ObjectMapper objectMapper;

    @Mock
    private Clock clock;

    private OutboxWorker outboxWorker;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        outboxWorker = new OutboxWorker(eventOutboxRepository, objectMapper, orderPlacedEventPublisher, clock);
    }

    @Test
    @DisplayName("publishBatch() 호출 시 PENDING 이벤트를 발행")
    void publishBatch_readsPendingOutbox_andPublishesEvent() {
        String payload = """
        {
          "items": [
            { "productCode": "P00000001", "decreasedStock": 1 }
          ],
          "userId": "test-aggregate-id",
          "eventId": "test-event-id",
          "userType": "GUEST",
          "orderPublicId": "test-order-id"
        }
        """;

        // given
        EventOutbox outbox = EventOutbox.builder()
                .eventId("test-event-id")
                .aggregateId("test-aggregate-id")
                .type("order.placed")
                .status(EventOutbox.Status.PENDING)
                .payloadJson(payload)
                .build();

        given(eventOutboxRepository.lockAndFetch(any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(outbox));


        // when
        outboxWorker.publishBatch();

        //then
        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);

        then(orderPlacedEventPublisher).should(times(1))
                .publishOrderPlacedSync(captor.capture());

        OrderPlacedEvent published = captor.getValue();

        assertThat(published.getItems().get(0).getProductCode()).isEqualTo("P00000001");
        assertThat(published.getUserType()).isEqualTo(UserType.GUEST);
        assertThat(published.getOrderPublicId()).isEqualTo("test-order-id");
        assertThat(outbox.getStatus()).isEqualTo(EventOutbox.Status.SENT);

    }
}
