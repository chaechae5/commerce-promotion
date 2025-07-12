package com.chae.promo.auth.dto;

import com.chae.promo.auth.domain.TokenType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class TokenValidationResponse {
    private TokenType tokenType;
    private String principalId;
}