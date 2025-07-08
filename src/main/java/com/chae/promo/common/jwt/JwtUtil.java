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
import java.util.UUID;

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
    public String generateAnonymousToken() {
        String anonId = UUID.randomUUID().toString();
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

    public String validateAndGetAnonId(String token) {
        var claims = validateToken(token);
        return claims.get("anonId", String.class);
    }

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
