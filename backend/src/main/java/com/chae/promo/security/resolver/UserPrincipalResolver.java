package com.chae.promo.security.resolver;

import io.jsonwebtoken.Claims;

// Claims에서 사용자 주체를 해석하는 역할을 정의하는 인터페이스
public interface UserPrincipalResolver {
    String resolve(Claims claims);
}
