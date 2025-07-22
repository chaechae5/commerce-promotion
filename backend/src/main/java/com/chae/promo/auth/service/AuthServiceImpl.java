package com.chae.promo.auth.service;

import com.chae.promo.auth.domain.AuthProviderType;
import com.chae.promo.auth.dto.TokenResponse;
import com.chae.promo.auth.dto.TokenValidationResponse;
import com.chae.promo.security.JwtUtil;
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
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;

    @Override
    public TokenResponse issueAnonymousTokens() {
        String anonId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(anonId, AuthProviderType.ANONYMOUS);
        String refreshToken = jwtUtil.generateRefreshToken(anonId, AuthProviderType.ANONYMOUS); // 추후 랜덤문자열 기반으로 변경 예정

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getJwtAccessTokenExpiration())
                .build();
    }

    @Override
    public TokenValidationResponse validateAndExtractToken(String token) {
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

    @Override
    public TokenResponse refreshTokens(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.validateToken(refreshToken);
        } catch (CommonCustomException e) {
            if (e.getErrorCode() == CommonErrorCode.JWT_EXPIRED) {
                log.warn("Refresh Token 만료: {}", e.getMessage());
                throw new CommonCustomException(CommonErrorCode.REFRESH_TOKEN_EXPIRED);
            } else if (e.getErrorCode() == CommonErrorCode.JWT_INVALID) {
                log.warn("Refresh Token 유효성 검증 실패 (JWT 형식 오류 등): {}", e.getMessage());
                throw new CommonCustomException(CommonErrorCode.INVALID_REFRESH_TOKEN);
            } else {
                log.error("Refresh Token 검증 중 예상치 못한 오류: {}", e.getMessage(), e);
                throw e;
            }
        }

        String principalId = extractPrincipalId(claims, CommonErrorCode.INVALID_REFRESH_TOKEN);
        AuthProviderType authProviderType = getAuthProviderTypeFromClaims(claims);

        // 새로운 토큰 발급
        String newAccessToken = jwtUtil.generateAccessToken(principalId, authProviderType);
        String newRefreshToken = jwtUtil.generateRefreshToken(principalId, authProviderType);

        log.info("토큰 갱신 완료. principalId: {}, provider: {}", principalId, authProviderType);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtUtil.getJwtAccessTokenExpiration())
                .tokenType("Bearer")
                .build();
    }


    private AuthProviderType getAuthProviderTypeFromClaims(Claims claims) {
        String subjectString = claims.getSubject();
        return AuthProviderType.fromValue(subjectString);
    }

    private String extractPrincipalId(Claims claims, CommonErrorCode errorCodeIfMissing) {
        String principalId = claims.get("principalId", String.class);
        if (principalId == null || principalId.isBlank()) {
            log.warn("principalId 클레임 누락");
            throw new CommonCustomException(errorCodeIfMissing);
        }
        return principalId;
    }
}
