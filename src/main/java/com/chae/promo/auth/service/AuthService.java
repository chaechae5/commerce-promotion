package com.chae.promo.auth.service;

import com.chae.promo.auth.dto.TokenValidationResponse;

public interface AuthService {

    String createAnonymousToken();
    TokenValidationResponse validateAndExtractToken(String token);
}
