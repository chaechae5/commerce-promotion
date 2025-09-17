package com.chae.promo.outbox.service;

import com.chae.promo.order.event.OrderPlacedEvent;
import com.chae.promo.order.event.OrderPlacedEventPublisher;
import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWorker {
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    private final OrderPlacedEventPublisher orderPlacedEventPublisher;

    private final Clock clock;


    private static final int BATCH = 200;

    // backoff: 10s * (retryCount+1)
    private Duration backoff(int retryCount) {
        return Duration.ofSeconds(10L * Math.max(1, retryCount + 1));
    }

    @Scheduled(fixedDelayString = "PT5S") // 5초마다 실행
    @Transactional
    public void publishBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<EventOutbox> rows = eventOutboxRepository.lockAndFetch(now, BATCH);

        if (rows.isEmpty()) {
             log.debug("[Outbox] no events to publish");
            return;
        }

        for (EventOutbox outbox : rows) {
            try {

                OrderPlacedEvent event =
                        objectMapper.readValue(outbox.getPayloadJson(), OrderPlacedEvent.class);


                // 이벤트 발행
                orderPlacedEventPublisher.publishOrderPlacedSync(event);

                // 성공 업데이트
                outbox.setStatus(EventOutbox.Status.SENT);
                outbox.setLastError(null);
                outbox.setNextRetryAt(LocalDateTime.now(clock));

                log.info("[Outbox] SENT type={}, eventId={}, aggregateId={}",
                        outbox.getType(), outbox.getEventId(), outbox.getAggregateId());


            } catch (Exception ex) {
                int next = outbox.getRetryCount() + 1;
                outbox.setStatus(EventOutbox.Status.FAILED); // 실패 상태로 두고 재시도
                outbox.setRetryCount(next);
                outbox.setNextRetryAt(LocalDateTime.now(clock).plus(backoff(next)));
                outbox.setLastError(ex.getClass().getSimpleName() + ":" + ex.getMessage());

                log.warn("[Outbox] FAIL type={}, eventId={}, retry={}, nextRetryAt={}, cause={}",
                        outbox.getType(), outbox.getEventId(), next, outbox.getNextRetryAt(), ex.toString());
            }
        }
    }
}
