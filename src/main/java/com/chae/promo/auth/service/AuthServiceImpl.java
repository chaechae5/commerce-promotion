package com.chae.promo.auth.service;

import com.chae.promo.common.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final JwtUtil jwtUtil;

    @Override
    public String createAnonymousToken() {
        return jwtUtil.generateAnonymousToken();
    }

    @Override
    public String getAnonIdFromToken(String token) {
        return jwtUtil.validateAndGetAnonId(token);
    }
}
