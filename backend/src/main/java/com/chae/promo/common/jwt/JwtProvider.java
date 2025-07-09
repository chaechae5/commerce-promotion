package com.chae.promo.common.jwt;

import com.chae.promo.auth.domain.AuthProviderType;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * 토큰 생성, 서명, 파싱, 검증만 담당
 */
@Component
public class JwtProvider {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.accessTokenExpiration}") //초 단위
    private long jwtAccessTokenExpiration;

    @Value("${jwt.refreshTokenExpiration}")//초 단위
    private long jwtRefreshTokenExpiration;

    private SecretKey secretKey;

    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * AccessToken 생성
     * @param principalId 토큰 생성할 principalId
     * @param authProviderType 어떤 로그인방식의 토큰인지
     * @return accessToken
     */
    public String generateAccessToken(String principalId, AuthProviderType authProviderType) {
        return createToken(principalId, authProviderType, jwtAccessTokenExpiration, "access");
    }

    /**
     * RefreshToken 생성
     * @param principalId 토큰 생성할 principalId
     * @return refreshToken
     */
    public String generateRefreshToken(String principalId, AuthProviderType authProviderType) {
        return createToken(principalId, authProviderType, jwtRefreshTokenExpiration, "refresh");
    }

    //토큰생성 로직
    private String createToken(String principalId, AuthProviderType authProviderType, long expirySeconds, String tokenType) {
        return Jwts.builder()
                .setSubject(authProviderType.getValue())
                .claim("principalId", principalId)
                .claim("tokenType", tokenType)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirySeconds * 1000L))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * accessToken의 만료 초 반환
     * @return 만료 초
     */
    public long getJwtAccessTokenExpiration(){
        return jwtAccessTokenExpiration;
    }

    /**
     * 토큰 검증하고 Claims 객체 반환 공통 메서드
     * @param token
     * @return Claims
     */
    public Claims validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new CommonCustomException(CommonErrorCode.JWT_INVALID);
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!"anonymous".equals(claims.getSubject())) {
                throw new CommonCustomException(CommonErrorCode.JWT_INVALID);
            }

            return claims;
        } catch (ExpiredJwtException e) {
            throw new CommonCustomException(CommonErrorCode.JWT_EXPIRED);
        } catch (JwtException e) {
            throw new CommonCustomException(CommonErrorCode.JWT_INVALID);
        }
    }
}
