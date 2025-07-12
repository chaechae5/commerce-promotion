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

        String principalId = extractPrincipalId(claims, CommonErrorCode.JWT_INVALID);
        AuthProviderType authProviderType = getAuthProviderTypeFromClaims(claims);

        //authProviderType 따른 분기 - 확장을 염두에 두고 switch문 사용
        return switch (authProviderType) {
            case ANONYMOUS -> buildTokenResponse(authProviderType, principalId);
            default -> {
                log.error("지원하지 않는 AuthProviderType: {}", authProviderType);
                throw new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
        };
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

    private TokenValidationResponse buildTokenResponse(AuthProviderType authProviderType, String principalId) {
        return TokenValidationResponse.builder()
                .authProviderType(authProviderType)
                .principalId(principalId)
                .build();
    }
}
