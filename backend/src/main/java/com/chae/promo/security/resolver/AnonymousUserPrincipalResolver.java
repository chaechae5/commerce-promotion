package com.chae.promo.security.resolver;

import io.jsonwebtoken.Claims;

public class AnonymousUserPrincipalResolver implements UserPrincipalResolver {
    @Override
    public String resolve(Claims claims) {
        return claims.get("principalId", String.class);
    }
}
