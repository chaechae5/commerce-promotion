package com.chae.promo.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer"; // 기본값 "Bearer"
    private Long expiresIn; // accessToken 만료 시간 (초 단위)
}