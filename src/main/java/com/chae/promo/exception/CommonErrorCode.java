package com.chae.promo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode {
    JWT_EXPIRED(HttpStatus.FORBIDDEN, "JWT_EXPIRED", "토큰이 만료되었습니다."),
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "JWT_INVALID", "토큰이 유효하지 않습니다.");


    CommonErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
