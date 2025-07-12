package com.chae.promo.common.jwt;

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
public class JwtUtil {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private SecretKey secretKey;

    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 익명 토큰 생성
     * @param anonId 익명 사용자 id
     * @return JWT 토큰
     */
    public String generateAnonymousToken(String anonId) {
        long now = System.currentTimeMillis();
        Date expiry = new Date(now + jwtExpirationMs);

        return Jwts.builder()
                .setSubject("anonymous")
                .claim("anonId", anonId)
                .setIssuedAt(new Date(now))
                .setExpiration(expiry)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 토큰 검증하고 Claims 객체 반환 공통 메서드
     * @param token
     * @return Claims
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new CommonCustomException(CommonErrorCode.JWT_EXPIRED);
        } catch (JwtException e) {
            throw new CommonCustomException(CommonErrorCode.JWT_INVALID);
        }
    }
}
