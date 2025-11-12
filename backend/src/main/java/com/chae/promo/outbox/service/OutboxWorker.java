package com.chae.promo.outbox.service;

import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
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
    private final Clock clock;

    private final List<DomainOutboxPublisher> publishers;


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

            DomainOutboxPublisher publisher = findPublisher(outbox.getType());
            if(publisher == null) {
                log.error("[Outbox] publisher not found. type={}, eventId={}", outbox.getType(), outbox.getEventId());
                continue;
            }

            try {
                publisher.publish(outbox.getPayloadJson());

                // 성공 처리
                outbox.markSent(clock);

                log.info("[Outbox] SENT type={}, eventId={}, aggregateId={}",
                        outbox.getType(), outbox.getEventId(), outbox.getAggregateId());

            } catch (JsonProcessingException jsonEx) {
                handleFailure(outbox, jsonEx, "Payload JSON 파싱 실패");

            } catch (KafkaException kafkaEx) {
                handleFailure(outbox, kafkaEx, "Kafka 전송 실패");

            } catch (Exception ex) {
                handleFailure(outbox, ex, "알 수 없는 오류 발생");
            }
        }
        eventOutboxRepository.saveAll(rows);

    }

    private void handleFailure(EventOutbox outbox, Exception ex, String reason) {
        int next = outbox.getRetryCount() + 1;
        outbox.markFailed(next, clock, ex, backoff(next));

        log.warn("[Outbox] FAIL type={}, eventId={}, retry={}, nextRetryAt={}, cause={}, reason={}",
                outbox.getType(), outbox.getEventId(), next, outbox.getNextRetryAt(), ex.toString(), reason);

    }

    private DomainOutboxPublisher findPublisher(String type) {
        return publishers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElse(null);
    }
}
