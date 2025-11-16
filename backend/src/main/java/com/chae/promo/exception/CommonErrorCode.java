package com.chae.promo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode {
    JWT_EXPIRED(HttpStatus.FORBIDDEN, "JWT_EXPIRED", "토큰이 만료되었습니다."),
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "JWT_INVALID", "토큰이 유효하지 않습니다."),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.UNAUTHORIZED, "UNSUPPORTED_AUTH_PROVIDER", "지원하지 않는 인증 프로바이더 타입입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "일시적인 시스템 장애입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,"REFRESH_TOKEN_EXPIRED", "리프레시 토큰이 만료되었습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력 값 유효성 검증에 실패했습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급된 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "쿠폰이 만료되었습니다."),
    COUPON_SOLD_OUT(HttpStatus.GONE, "COUPON_SOLD_OUT", "쿠폰이 소진되었습니다."),
    COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "COUPON_OUT_OF_STOCK", "쿠폰이 소진되었습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다."),
    COUPON_ISSUE_SAVE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "COUPON_ISSUE_SAVE_FAIL", "쿠폰 발급 처리 중 오류가 발생했습니다."),
    COUPON_ISSUE_DATA_ACCESS_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "COUPON_ISSUE_DATA_ACCESS_FAIL", "쿠폰 발급 처리 중 오류가 발생했습니다."),

    //주문
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "요청하신 상품을 찾을 수 없습니다."),
    PRODUCT_NOT_FOR_SALE(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_FOR_SALE", "현재 판매하지 않는 상품입니다."),
    INVALID_PRODUCT_CODE(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_CODE", "유효하지 않은 상품 코드입니다."),
    PRODUCT_SOLD_OUT(HttpStatus.BAD_REQUEST, "STOCK_SOLD_OUT", "상품 재고가 부족합니다."),
    PRODUCT_STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_STOCK_NOT_FOUND", "상품 재고 정보를 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "요청 수량만큼 재고가 충분하지 않습니다."),
    PRODUCT_STOCK_AUDIT_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_STOCK_AUDIT_SAVE_FAILED", "상품 재고 감사 기록을 저장하는 중 오류가 발생했습니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "유효하지않은 수량입니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "해당 주문을 찾을 수 없습니다."),
    NOT_ALLOWED(HttpStatus.NOT_FOUND, "NOT_ALLOWED", "접근 권한이 없습니다."),
    PAYMENT_NOT_ALLOWED_STATE(HttpStatus.NOT_FOUND, "PAYMENT_NOT_ALLOWED_STATE", "결제할 수 없는 주문 상태입니다."),
    PAYMENT_APPROVAL_FAILED(HttpStatus.NOT_FOUND, "PAYMENT_APPROVAL_FAILED", "결제 승인에 실패했습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.NOT_FOUND, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_ORDER_MISMATCH(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_MISMATCH", "결제 주문 정보가 일치하지 않습니다."),
    PAYMENT_NOT_SUPPORTED_PG_TYPE(HttpStatus.NOT_FOUND, "PAYMENT_NOT_SUPPORTED_PG_TYPE", "지원하지 않는 결제 PG 타입입니다."),
    ALREADY_PAID(HttpStatus.NOT_FOUND, "ALREADY_PAID", "이미 결제된 주문입니다."),
    DUPLICATED_PAYMENT_METHOD(HttpStatus.NOT_FOUND, "DUPLICATED_PAYMENT_METHOD", "중복된 결제 수단이 포함되어 있습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),

    //이벤트
    INVALID_EVENT_ID(HttpStatus.BAD_REQUEST, "INVALID_EVENT_ID", "유효하지 않은 이벤트 ID입니다."),

    //redis
    REDIS_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_OPERATION_FAILED", "Redis 작업 중 오류가 발생했습니다."),
    REDIS_STOCK_HOLD_MISSING_OR_EXPIRED(HttpStatus.NOT_FOUND, "REDIS_STOCK_HOLD_MISSING_OR_EXPIRED", "Redis 재고 hold 정보가 없거나 만료되었습니다."),
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
