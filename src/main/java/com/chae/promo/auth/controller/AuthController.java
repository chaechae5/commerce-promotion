package com.chae.promo.auth.controller;

import com.chae.promo.auth.dto.TokenRefreshRequest;
import com.chae.promo.auth.dto.TokenResponse;
import com.chae.promo.auth.dto.TokenValidationResponse;
import com.chae.promo.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증 api")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token/new/anonymous")
    @Operation(summary = "비회원 토큰 발급", description = "인증없이 토큰 발급")
    public ResponseEntity<TokenResponse> issueAnonymousToken() {
        return ResponseEntity.ok(authService.issueAnonymousTokens());
    }

    @GetMapping("/token/verify")
    @Operation(summary = "토큰 검증", description = "토큰 검증 공통 API")
    public ResponseEntity<TokenValidationResponse> verifyToken(
            @RequestHeader("Authorization") String token
    ) {
        return ResponseEntity.ok(authService.validateAndExtractToken(token));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            @RequestBody @Valid TokenRefreshRequest request
    ) {
        TokenResponse response = authService.refreshTokens(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }


}
