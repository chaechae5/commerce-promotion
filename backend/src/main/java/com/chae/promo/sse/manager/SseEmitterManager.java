package com.chae.promo.sse.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseEmitterManager {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(String clientId) {
        log.info("SSE 연결 요청: clientId={}", clientId);

        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis()); // 10분 타임아웃
        emitters.put(clientId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료: clientId={}", clientId);
            emitters.remove(clientId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE 연결 타임아웃: clientId={}", clientId);
            emitters.remove(clientId);
        });

        emitter.onError((Throwable ex) -> {
            log.warn("SSE 연결 에러 : clientId={}, ex={}", clientId, ex.toString());
            emitters.remove(clientId); //이미 에러가 난 후이므로 completeWithError 호출 불필요
        });

        // 연결 확인용 첫 이벤트 전송
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            closeWithError(clientId, emitter, e);
        }

        return emitter;
    }

    public void sendToAll(String event, String data) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                log.warn("SSE 전송 실패 (client={}): {}", clientId, e.getMessage());
                closeWithError(clientId, emitter, e);}
        });
    }

    // 에러 발생 시 연결 종료 처리
    private void closeWithError(String clientId, SseEmitter emitter, Exception e) {
        emitters.remove(clientId);
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // 이미 완료/타임아웃된 케이스 등: 무시
        }
    }
}
