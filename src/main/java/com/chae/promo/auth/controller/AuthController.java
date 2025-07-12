package com.chae.promo.auth.controller;

import com.chae.promo.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증 api")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/anonymous/token")
    @Operation(summary = "비회원 토큰 발급", description = "인증없이 토큰 발급")
    public String issueAnonymousToken() {
        return authService.createAnonymousToken();
    }

    @GetMapping("/anonymous/token/verify")
    @Operation(summary = "비회원 토큰 검증", description = "토큰 검증")
    public String verifyAnonymousToken(
            @RequestHeader("Authorization") String token
    ) {
        return authService.getAnonIdFromToken(token);
    }

}
