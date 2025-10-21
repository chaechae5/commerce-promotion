package com.chae.promo.event.controller;

import com.chae.promo.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    //TODO: 추후 관리자 인증/권한 검증 로직 추가 예정
    @PostMapping("/{eventId}/schedule")
    public ResponseEntity<String> scheduleEvent(
            @PathVariable String eventId,
            @RequestParam long delay) {
        eventService.scheduleEvent(eventId, delay);

        return ResponseEntity.ok("Event " + eventId + " scheduled in " + delay + " seconds");
    }
}
