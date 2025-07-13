package com.chae.promo.auth.dto;

import com.chae.promo.auth.domain.AuthProviderType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class TokenValidationResponse {
    private AuthProviderType authProviderType;
    private String principalId;
}