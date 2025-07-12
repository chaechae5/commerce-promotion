package com.chae.promo.auth.service;

import com.chae.promo.auth.domain.TokenType;
import com.chae.promo.auth.dto.TokenValidationResponse;
import com.chae.promo.common.jwt.JwtUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final JwtUtil jwtUtil;

    @Override
    public String createAnonymousToken() {
        String anonId = UUID.randomUUID().toString();
        return jwtUtil.generateAnonymousToken(anonId);
    }

    @Override
    public TokenValidationResponse validateAndExtractToken(String token) {
        //접두사 있으면 제거
        String cleanToken = extractTokenFromBearer(token);

        Claims claims = jwtUtil.validateToken(cleanToken);

        // Claims에서 얻은 String subject를 TokenType enum으로 변환
        String subject = claims.getSubject();
        TokenType tokenType = TokenType.fromValue(subject);

        //tokenType 따른 분기 - 확장을 염두에 두고 switch문 사용
        switch (tokenType) {
            case ANONYMOUS -> {
                String annoId = claims.get("anonId", String.class);
                return buildTokenResponse(TokenType.ANONYMOUS, annoId);
            }
            // 향후 다른 토큰 타입 추가 가능
            default -> {
                //예상치 못한 tokenType일 경우 예외
                log.error("TokenType.fromValue에서 처리되지 않은 예상치 못한 토큰 타입이 switch문에 도달: {}", tokenType);
                throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Bearer 접두사가 있으면 제거하고 순수 토큰만 반환
     */
    private String extractTokenFromBearer(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new CommonCustomException(CommonErrorCode.JWT_INVALID);
        }

        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private TokenValidationResponse buildTokenResponse(TokenType tokenType, String principalId) {
        return TokenValidationResponse.builder()
                .tokenType(tokenType)
                .principalId(principalId)
                .build();
    }
}
