package com.chae.promo.sse.controller;

import com.chae.promo.sse.manager.SseEmitterManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("api/sse")
@RequiredArgsConstructor
@Tag(name = "SSE api")
public class SseController {

    private final SseEmitterManager emitterManager;

    @GetMapping("/connect/{clientId}")
    @Operation(summary = "SSE 연결")
    public SseEmitter connect(@PathVariable String clientId) {
        return emitterManager.connect(clientId);
    }
}
