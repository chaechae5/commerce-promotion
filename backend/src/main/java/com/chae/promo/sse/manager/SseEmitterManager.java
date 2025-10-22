package com.chae.promo.sse.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseEmitterManager {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(String clientId) {
        log.info("SSE 연결 요청: clientId={}", clientId);

        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis()); // 30분 타임아웃
        emitters.put(clientId, emitter);
        emitter.onCompletion(() -> emitters.remove(clientId));
        emitter.onTimeout(() -> emitters.remove(clientId));
        return emitter;
    }

    public void sendToAll(String event, String data) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                emitters.remove(clientId);
                log.warn("SSE 전송 실패 (client={}): {}", clientId, e.getMessage());
            }
        });
    }
}
