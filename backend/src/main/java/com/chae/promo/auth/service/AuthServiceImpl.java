package com.chae.promo.auth.service;

import com.chae.promo.auth.domain.AuthProviderType;
import com.chae.promo.auth.dto.TokenResponse;
import com.chae.promo.auth.dto.TokenValidationResponse;
import com.chae.promo.exception.AuthException;
import com.chae.promo.security.JwtUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private final String KEY_PREFIX = "refreshToken:";


    @Override
    public TokenResponse issueAnonymousTokens() {
        String anonId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(anonId, AuthProviderType.ANONYMOUS);
        String refreshToken = jwtUtil.generateRefreshToken(anonId, AuthProviderType.ANONYMOUS); // 추후 랜덤문자열 기반으로 변경 예정
        long refreshTokenExpirationSeconds = jwtUtil.getJwtAccessTokenExpiration();

        redisSetRefreshToken(anonId, refreshToken, refreshTokenExpirationSeconds);

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

        // 토큰 타입이 'refresh'가 맞는지 확인
        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw new AuthException(CommonErrorCode.INVALID_REFRESH_TOKEN);
        }

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

        validateRefreshTokenWithStored(principalId, refreshToken);

        // 새로운 토큰 발급
        String newAccessToken = jwtUtil.generateAccessToken(principalId, authProviderType);
        String newRefreshToken = jwtUtil.generateRefreshToken(principalId, authProviderType);
        long refreshTokenExpirationSeconds = jwtUtil.getJwtAccessTokenExpiration();

        redisSetRefreshToken(principalId, newRefreshToken, refreshTokenExpirationSeconds);

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

    private void validateRefreshTokenWithStored(String principalId, String clientRefreshToken) {
        String storedToken = redisTemplate.opsForValue().get(KEY_PREFIX + principalId);

        if (storedToken == null) {
            // Redis에 토큰이 없으면, 만료되었거나 잘못된 요청으로 간주
            throw new AuthException(CommonErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        if (!storedToken.equals(clientRefreshToken)) {
            // 저장된 토큰과 일치하지 않으면, 탈취되었을 가능성이 있으므로 토큰 삭제 후 에러 발생
            log.warn("Refresh Token 불일치 감지 (탈취 가능성). principalId: {}", principalId);
            redisTemplate.delete(KEY_PREFIX + principalId);
            throw new AuthException(CommonErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void redisSetRefreshToken(String userId, String token, long ttl){
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId,
                token,
                Duration.ofSeconds(ttl));
    }
}
