package com.chae.promo.event.scheduler;

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


    @Scheduled(fixedDelay = 60000)    // 1분마다 TTL 유실 복구
    public void recoverMissedEvents() {
        List<String> missed = eventService.getExpiredPendingEvents();

        if (missed.isEmpty()) return;

        for (String eventId : missed) {
            try{
                if (!eventService.isAlreadyOpened(eventId)) {
                    log.warn("TTL 이벤트 유실 감지됨, 강제 오픈 처리: eventId={}", eventId);

                    eventService.markEventAsOpened(eventId);
                }
            }catch (Exception e) {
                log.error("TTL 이벤트 유실 복구 중 오류 발생: eventId={}", eventId, e);
            } finally {
                eventService.removeFromSchedule(eventId);

            }

        }
    }
}
