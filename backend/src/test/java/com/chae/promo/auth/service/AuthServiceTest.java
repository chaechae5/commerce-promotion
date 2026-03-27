/*
 * Unit tests for AuthServiceImpl covering:
 *   - issueAnonymousTokens()       : 정상 토큰 발급, 반환값 구조, Redis 저장 호출 검증
 *   - validateAndExtractToken()    : 정상 검증, Bearer 접두사 제거, null/blank 입력, 만료/invalid 토큰 예외
 *   - refreshTokens()              : 정상 갱신, Redis 불일치 → INVALID_REFRESH_TOKEN, Redis 없음 → REFRESH_TOKEN_EXPIRED,
 *                                    JWT 만료 → REFRESH_TOKEN_EXPIRED, JWT invalid → INVALID_REFRESH_TOKEN,
 *                                    principalId 누락, 예상치 못한 예외 재전파
 */
package com.chae.promo.auth.service;

import com.chae.promo.auth.domain.AuthProviderType;
import com.chae.promo.auth.dto.TokenResponse;
import com.chae.promo.auth.dto.TokenValidationResponse;
import com.chae.promo.exception.AuthException;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthServiceImpl 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    // -----------------------------------------------------------------------
    // issueAnonymousTokens()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("issueAnonymousTokens()")
    class IssueAnonymousTokensTest {

        @Test
        @DisplayName("정상 발급: accessToken, refreshToken, tokenType, expiresIn 모두 반환된다")
        void issueAnonymousTokens_returnsCompleteTokenResponse() {
            // Arrange
            long expiration = 3600L;
            when(jwtUtil.generateAccessToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("access.token.value");
            when(jwtUtil.generateRefreshToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("refresh.token.value");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(expiration);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            TokenResponse response = authService.issueAnonymousTokens();

            // Assert
            assertThat(response.getAccessToken()).isEqualTo("access.token.value");
            assertThat(response.getRefreshToken()).isEqualTo("refresh.token.value");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(expiration);
        }

        @Test
        @DisplayName("정상 발급: generateAccessToken 에 UUID 형식의 anonId가 전달된다")
        void issueAnonymousTokens_passesUuidAsAnonId() {
            // Arrange
            when(jwtUtil.generateAccessToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("access.token.value");
            when(jwtUtil.generateRefreshToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("refresh.token.value");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(3600L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ArgumentCaptor<String> anonIdCaptor = ArgumentCaptor.forClass(String.class);

            // Act
            authService.issueAnonymousTokens();

            // Assert — UUID 형식 검증 (8-4-4-4-12)
            verify(jwtUtil).generateAccessToken(anonIdCaptor.capture(), eq(AuthProviderType.ANONYMOUS));
            String capturedId = anonIdCaptor.getValue();
            assertThat(capturedId).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
            );
        }

        @Test
        @DisplayName("정상 발급: 동일한 anonId로 accessToken과 refreshToken이 모두 생성된다")
        void issueAnonymousTokens_sameAnonIdUsedForBothTokens() {
            // Arrange
            when(jwtUtil.generateAccessToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("access.token.value");
            when(jwtUtil.generateRefreshToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("refresh.token.value");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(3600L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ArgumentCaptor<String> accessIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> refreshIdCaptor = ArgumentCaptor.forClass(String.class);

            // Act
            authService.issueAnonymousTokens();

            // Assert
            verify(jwtUtil).generateAccessToken(accessIdCaptor.capture(), any());
            verify(jwtUtil).generateRefreshToken(refreshIdCaptor.capture(), any());
            assertThat(accessIdCaptor.getValue()).isEqualTo(refreshIdCaptor.getValue());
        }

        @Test
        @DisplayName("정상 발급: Redis에 refreshToken이 KEY_PREFIX + anonId 키로 저장된다")
        void issueAnonymousTokens_storesRefreshTokenInRedisWithCorrectKey() {
            // Arrange
            String refreshTokenValue = "refresh.token.value";
            long expiration = 3600L;

            when(jwtUtil.generateAccessToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn("access.token.value");
            when(jwtUtil.generateRefreshToken(anyString(), eq(AuthProviderType.ANONYMOUS)))
                    .thenReturn(refreshTokenValue);
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(expiration);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ArgumentCaptor<String> anonIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> redisKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> redisValueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Duration> redisTtlCaptor = ArgumentCaptor.forClass(Duration.class);

            // Act
            authService.issueAnonymousTokens();

            // Assert
            verify(jwtUtil).generateAccessToken(anonIdCaptor.capture(), any());
            verify(valueOperations).set(redisKeyCaptor.capture(), redisValueCaptor.capture(), redisTtlCaptor.capture());

            assertThat(redisKeyCaptor.getValue()).isEqualTo("refreshToken:" + anonIdCaptor.getValue());
            assertThat(redisValueCaptor.getValue()).isEqualTo(refreshTokenValue);
            assertThat(redisTtlCaptor.getValue()).isEqualTo(Duration.ofSeconds(expiration));
        }

        @Test
        @DisplayName("정상 발급: 호출할 때마다 서로 다른 anonId(UUID)가 생성된다")
        void issueAnonymousTokens_generatesDifferentAnonIdEachCall() {
            // Arrange
            when(jwtUtil.generateAccessToken(anyString(), any())).thenReturn("token");
            when(jwtUtil.generateRefreshToken(anyString(), any())).thenReturn("refresh");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(3600L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ArgumentCaptor<String> firstCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> secondCaptor = ArgumentCaptor.forClass(String.class);

            // Act
            authService.issueAnonymousTokens();
            verify(jwtUtil).generateAccessToken(firstCaptor.capture(), any());
            clearInvocations(jwtUtil, redisTemplate, valueOperations);

            authService.issueAnonymousTokens();
            verify(jwtUtil).generateAccessToken(secondCaptor.capture(), any());

            // Assert
            assertThat(firstCaptor.getValue()).isNotEqualTo(secondCaptor.getValue());
        }
    }

    // -----------------------------------------------------------------------
    // validateAndExtractToken()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validateAndExtractToken()")
    class ValidateAndExtractTokenTest {

        @Test
        @DisplayName("정상 검증: 순수 토큰 문자열로 Claims를 파싱하고 TokenValidationResponse를 반환한다")
        void validateAndExtractToken_validToken_returnsResponse() {
            // Arrange
            String rawToken = "valid.jwt.token";
            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn("user-principal-id");
            when(jwtUtil.validateToken(rawToken)).thenReturn(claims);

            // Act
            TokenValidationResponse response = authService.validateAndExtractToken(rawToken);

            // Assert
            assertThat(response.getAuthProviderType()).isEqualTo(AuthProviderType.ANONYMOUS);
            assertThat(response.getPrincipalId()).isEqualTo("user-principal-id");
        }

        @Test
        @DisplayName("Bearer 접두사 제거: 'Bearer ' 접두사가 있을 때 접두사를 제거한 순수 토큰으로 검증한다")
        void validateAndExtractToken_bearerPrefix_stripsAndValidates() {
            // Arrange
            String rawToken = "actual.jwt.token";
            String bearerToken = "Bearer " + rawToken;
            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn("user-principal-id");
            when(jwtUtil.validateToken(rawToken)).thenReturn(claims);

            // Act
            TokenValidationResponse response = authService.validateAndExtractToken(bearerToken);

            // Assert — 순수 토큰으로 validateToken이 호출되어야 한다
            verify(jwtUtil).validateToken(rawToken);
            assertThat(response.getPrincipalId()).isEqualTo("user-principal-id");
        }

        @Test
        @DisplayName("null 토큰: JWT_INVALID CommonCustomException이 발생한다")
        void validateAndExtractToken_nullToken_throwsJwtInvalid() {
            assertThatThrownBy(() -> authService.validateAndExtractToken(null))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_INVALID));
        }

        @Test
        @DisplayName("빈 문자열 토큰: JWT_INVALID CommonCustomException이 발생한다")
        void validateAndExtractToken_emptyToken_throwsJwtInvalid() {
            assertThatThrownBy(() -> authService.validateAndExtractToken(""))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_INVALID));
        }

        @Test
        @DisplayName("공백만 있는 토큰: JWT_INVALID CommonCustomException이 발생한다")
        void validateAndExtractToken_blankToken_throwsJwtInvalid() {
            assertThatThrownBy(() -> authService.validateAndExtractToken("   "))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_INVALID));
        }

        @Test
        @DisplayName("만료된 토큰: jwtUtil이 JWT_EXPIRED 예외를 던지면 그대로 전파된다")
        void validateAndExtractToken_expiredToken_propagatesException() {
            // Arrange
            String expiredToken = "expired.jwt.token";
            when(jwtUtil.validateToken(expiredToken))
                    .thenThrow(new AuthException(CommonErrorCode.JWT_EXPIRED));

            // Act & Assert
            assertThatThrownBy(() -> authService.validateAndExtractToken(expiredToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_EXPIRED));
        }

        @Test
        @DisplayName("유효하지 않은 토큰: jwtUtil이 JWT_INVALID 예외를 던지면 그대로 전파된다")
        void validateAndExtractToken_invalidToken_propagatesException() {
            // Arrange
            String invalidToken = "invalid.jwt.token";
            when(jwtUtil.validateToken(invalidToken))
                    .thenThrow(new AuthException(CommonErrorCode.JWT_INVALID));

            // Act & Assert
            assertThatThrownBy(() -> authService.validateAndExtractToken(invalidToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_INVALID));
        }

        @Test
        @DisplayName("지원하지 않는 provider: switch default 분기에서 INTERNAL_SERVER_ERROR 예외가 발생한다")
        void validateAndExtractToken_unsupportedProvider_throwsInternalServerError() {
            // AuthProviderType.fromValue()가 AuthException(UNSUPPORTED_AUTH_PROVIDER)를 던져
            // switch 이전에 예외가 발생하는 경로를 검증한다.
            // 현재 AuthProviderType은 ANONYMOUS 하나뿐이므로,
            // claims의 provider가 알 수 없는 값이면 fromValue()에서 예외가 난다.
            String rawToken = "valid.jwt.token";
            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("UNKNOWN_PROVIDER");
            when(claims.getSubject()).thenReturn("some-principal");
            when(jwtUtil.validateToken(rawToken)).thenReturn(claims);

            assertThatThrownBy(() -> authService.validateAndExtractToken(rawToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.UNSUPPORTED_AUTH_PROVIDER));
        }

        @Test
        @DisplayName("principalId 누락: subject가 null이면 JWT_INVALID 예외가 발생한다")
        void validateAndExtractToken_missingPrincipalId_throwsJwtInvalid() {
            // Arrange — provider stub 불필요: extractPrincipalId가 먼저 예외를 발생시킴
            String rawToken = "valid.jwt.token";
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn(null);
            when(jwtUtil.validateToken(rawToken)).thenReturn(claims);

            // Act & Assert
            assertThatThrownBy(() -> authService.validateAndExtractToken(rawToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_INVALID));
        }

        @Test
        @DisplayName("principalId 누락: subject가 blank이면 JWT_INVALID 예외가 발생한다")
        void validateAndExtractToken_blankPrincipalId_throwsJwtInvalid() {
            // Arrange — provider stub 불필요: extractPrincipalId가 먼저 예외를 발생시킴
            String rawToken = "valid.jwt.token";
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn("  ");
            when(jwtUtil.validateToken(rawToken)).thenReturn(claims);

            // Act & Assert
            assertThatThrownBy(() -> authService.validateAndExtractToken(rawToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.JWT_INVALID));
        }
    }

    // -----------------------------------------------------------------------
    // refreshTokens()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("refreshTokens()")
    class RefreshTokensTest {

        @Test
        @DisplayName("정상 갱신: 새 accessToken, refreshToken을 반환하고 Redis에 새 refreshToken을 저장한다")
        void refreshTokens_validToken_returnsNewTokenResponse() {
            // Arrange
            String oldRefreshToken = "old.refresh.token";
            String principalId = "user-123";
            long expiration = 3600L;

            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn(principalId);
            when(jwtUtil.validateToken(oldRefreshToken)).thenReturn(claims);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refreshToken:" + principalId)).thenReturn(oldRefreshToken);

            when(jwtUtil.generateAccessToken(principalId, AuthProviderType.ANONYMOUS))
                    .thenReturn("new.access.token");
            when(jwtUtil.generateRefreshToken(principalId, AuthProviderType.ANONYMOUS))
                    .thenReturn("new.refresh.token");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(expiration);

            // Act
            TokenResponse response = authService.refreshTokens(oldRefreshToken);

            // Assert
            assertThat(response.getAccessToken()).isEqualTo("new.access.token");
            assertThat(response.getRefreshToken()).isEqualTo("new.refresh.token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(expiration);
        }

        @Test
        @DisplayName("정상 갱신: 새 refreshToken이 Redis에 올바른 키와 TTL로 저장된다")
        void refreshTokens_validToken_storesNewRefreshTokenInRedis() {
            // Arrange
            String oldRefreshToken = "old.refresh.token";
            String principalId = "user-123";
            long expiration = 3600L;

            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn(principalId);
            when(jwtUtil.validateToken(oldRefreshToken)).thenReturn(claims);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refreshToken:" + principalId)).thenReturn(oldRefreshToken);

            when(jwtUtil.generateAccessToken(principalId, AuthProviderType.ANONYMOUS))
                    .thenReturn("new.access.token");
            when(jwtUtil.generateRefreshToken(principalId, AuthProviderType.ANONYMOUS))
                    .thenReturn("new.refresh.token");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(expiration);

            // Act
            authService.refreshTokens(oldRefreshToken);

            // Assert
            verify(valueOperations).set(
                    eq("refreshToken:" + principalId),
                    eq("new.refresh.token"),
                    eq(Duration.ofSeconds(expiration))
            );
        }

        @Test
        @DisplayName("Redis 토큰 없음: REFRESH_TOKEN_EXPIRED AuthException이 발생한다")
        void refreshTokens_noTokenInRedis_throwsRefreshTokenExpired() {
            // Arrange
            String refreshToken = "some.refresh.token";
            String principalId = "user-123";

            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn(principalId);
            when(jwtUtil.validateToken(refreshToken)).thenReturn(claims);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refreshToken:" + principalId)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(refreshToken))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.REFRESH_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("Redis 토큰 불일치: INVALID_REFRESH_TOKEN AuthException이 발생하고 Redis 키가 삭제된다")
        void refreshTokens_tokenMismatch_throwsInvalidRefreshTokenAndDeletesRedisKey() {
            // Arrange
            String clientToken = "client.refresh.token";
            String storedToken = "different.stored.token";
            String principalId = "user-123";

            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn(principalId);
            when(jwtUtil.validateToken(clientToken)).thenReturn(claims);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refreshToken:" + principalId)).thenReturn(storedToken);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(clientToken))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.INVALID_REFRESH_TOKEN));

            verify(redisTemplate).delete("refreshToken:" + principalId);
        }

        @Test
        @DisplayName("JWT 만료: JWT_EXPIRED가 REFRESH_TOKEN_EXPIRED CommonCustomException으로 변환된다")
        void refreshTokens_expiredJwt_throwsRefreshTokenExpired() {
            // Arrange
            String expiredToken = "expired.refresh.token";
            when(jwtUtil.validateToken(expiredToken))
                    .thenThrow(new AuthException(CommonErrorCode.JWT_EXPIRED));

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(expiredToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.REFRESH_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("JWT invalid: JWT_INVALID가 INVALID_REFRESH_TOKEN CommonCustomException으로 변환된다")
        void refreshTokens_invalidJwt_throwsInvalidRefreshToken() {
            // Arrange
            String invalidToken = "tampered.refresh.token";
            when(jwtUtil.validateToken(invalidToken))
                    .thenThrow(new AuthException(CommonErrorCode.JWT_INVALID));

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(invalidToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("예상치 못한 CommonCustomException: 다른 errorCode의 예외는 그대로 재전파된다")
        void refreshTokens_unexpectedCommonCustomException_rethrows() {
            // Arrange
            String token = "some.refresh.token";
            when(jwtUtil.validateToken(token))
                    .thenThrow(new CommonCustomException(CommonErrorCode.INTERNAL_SERVER_ERROR));

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(token))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
        }

        @Test
        @DisplayName("principalId 누락: subject가 null이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
        void refreshTokens_missingPrincipalId_throwsInvalidRefreshToken() {
            // Arrange — provider stub 불필요: extractPrincipalId가 먼저 예외를 발생시킴
            String refreshToken = "some.refresh.token";
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn(null);
            when(jwtUtil.validateToken(refreshToken)).thenReturn(claims);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(refreshToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("principalId 누락: subject가 blank이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
        void refreshTokens_blankPrincipalId_throwsInvalidRefreshToken() {
            // Arrange — provider stub 불필요: extractPrincipalId가 먼저 예외를 발생시킴
            String refreshToken = "some.refresh.token";
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn("   ");
            when(jwtUtil.validateToken(refreshToken)).thenReturn(claims);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(refreshToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("지원하지 않는 provider: UNSUPPORTED_AUTH_PROVIDER 예외가 발생하고 Redis 저장은 호출되지 않는다")
        void refreshTokens_unsupportedProvider_throwsAndDoesNotStoreInRedis() {
            // Arrange
            String refreshToken = "some.refresh.token";
            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("UNKNOWN_PROVIDER");
            when(claims.getSubject()).thenReturn("user-123");
            when(jwtUtil.validateToken(refreshToken)).thenReturn(claims);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshTokens(refreshToken))
                    .isInstanceOf(CommonCustomException.class)
                    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                            .isEqualTo(CommonErrorCode.UNSUPPORTED_AUTH_PROVIDER));

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("정상 갱신: 토큰 갱신 후 generateAccessToken과 generateRefreshToken이 정확한 인자로 호출된다")
        void refreshTokens_validToken_invokesTokenGenerationWithCorrectArgs() {
            // Arrange
            String oldRefreshToken = "old.refresh.token";
            String principalId = "user-xyz";

            Claims claims = mock(Claims.class);
            when(claims.get("provider", String.class)).thenReturn("ANONYMOUS");
            when(claims.getSubject()).thenReturn(principalId);
            when(jwtUtil.validateToken(oldRefreshToken)).thenReturn(claims);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refreshToken:" + principalId)).thenReturn(oldRefreshToken);

            when(jwtUtil.generateAccessToken(anyString(), any())).thenReturn("new.access");
            when(jwtUtil.generateRefreshToken(anyString(), any())).thenReturn("new.refresh");
            when(jwtUtil.getJwtAccessTokenExpiration()).thenReturn(3600L);

            // Act
            authService.refreshTokens(oldRefreshToken);

            // Assert
            verify(jwtUtil).generateAccessToken(principalId, AuthProviderType.ANONYMOUS);
            verify(jwtUtil).generateRefreshToken(principalId, AuthProviderType.ANONYMOUS);
        }
    }
}
