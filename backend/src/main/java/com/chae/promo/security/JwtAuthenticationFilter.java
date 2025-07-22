package com.chae.promo.security;

import com.chae.promo.auth.domain.AuthProviderType;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Authorization 헤더에서 토큰을 가져옵니다.
        String authorizationHeader = request.getHeader("Authorization");

        // 헤더 있는 경우에만 로직 수행
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {

            String token = authorizationHeader.substring(7);

            // 유효성 검증 & 정보 가져오기
            //todo. user table 설계 후 해당 로직 수정
            Claims claims = jwtUtil.validateToken(token);
            AuthProviderType authProviderType = AuthProviderType.fromValue(claims.getSubject());
            String userId = authProviderType.getResolver().resolve(claims);

            // DB 조회 없이, 클레임 정보로 UserDetails 객체를 생성
            UserDetails userDetails = new User(
                    userId,
                    "",
                    Collections.emptyList()
            );

            // Authentication 객체를 생성하고 SecurityContext에 저장
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // SecurityContext에 Authentication 객체 저장
            //    이제 이 요청은 '인증된' 요청으로 처리됩니다.
            SecurityContextHolder.getContext().setAuthentication(authentication);

        }

        // 다음 필터로 요청을 전달합니다.
        filterChain.doFilter(request, response);
    }
}