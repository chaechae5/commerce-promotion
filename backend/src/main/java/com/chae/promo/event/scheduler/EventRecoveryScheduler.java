package com.chae.promo.event.scheduler;

import com.chae.promo.event.kafka.EventKafkaPublisher;
import com.chae.promo.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventRecoveryScheduler {

    private final EventService eventService;
    private final EventKafkaPublisher eventKafkaPublisher;


    @Scheduled(fixedDelay = 60000)    // 1분마다 TTL 유실 복구
    public void recoverMissedEvents() {
        List<String> missed = eventService.getExpiredPendingEvents();

        if (missed.isEmpty()) return;

        for (String eventId : missed) {
            if (!eventService.isAlreadyOpened(eventId)) {
                log.warn("TTL 이벤트 유실 감지됨, 강제 오픈 처리: eventId={}", eventId);

                eventService.markEventAsOpened(eventId);
                eventKafkaPublisher.publishEventOpen(eventId);
            }
        }
    }
}
