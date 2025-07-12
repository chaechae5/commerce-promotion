package com.chae.promo.auth.service;

import com.chae.promo.auth.dto.TokenResponse;
import com.chae.promo.auth.dto.TokenValidationResponse;

public interface AuthService {

    TokenResponse issueAnonymousTokens();
    TokenValidationResponse validateAndExtractToken(String token);

    TokenResponse refreshTokens(String refreshToken);
}
