package com.chae.promo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode {
    JWT_EXPIRED(HttpStatus.FORBIDDEN, "JWT_EXPIRED", "토큰이 만료되었습니다."),
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "JWT_INVALID", "토큰이 유효하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "알 수 없는 오류입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,"REFRESH_TOKEN_EXPIRED", "리프레시 토큰이 만료되었습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력 값 유효성 검증에 실패했습니다."),
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "JWT_INVALID", "토큰이 유효하지 않습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급된 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "쿠폰이 만료되었습니다."),
    COUPON_SOLD_OUT(HttpStatus.GONE, "COUPON_SOLD_OUT", "쿠폰이 소진되었습니다."),
    COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "COUPON_OUT_OF_STOCK", "쿠폰이 소진되었습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다."),
    COUPON_ISSUE_SAVE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "COUPON_ISSUE_SAVE_FAIL", "쿠폰 발급 처리 중 오류가 발생했습니다."),

    ;

    CommonErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
