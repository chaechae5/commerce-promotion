package com.chae.promo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommonCustomException.class)
    protected ResponseEntity<CommonErrorResponse> handleUserCustomException(CommonCustomException e, HttpServletRequest request) {

        logException(e, request);
        return createErrorResponse(e.getErrorCode(), request);
    }

    private ResponseEntity<CommonErrorResponse> createErrorResponse(CommonErrorCode errorCode, HttpServletRequest request) {
        return CommonErrorResponse.toResponseEntity(errorCode, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class) // 처리되지 않은 일반 예외에 대한 포괄적 처리
    protected ResponseEntity<CommonErrorResponse> handleUnhandledException(Exception e, HttpServletRequest request) {
        log.error("예상치 못한 서버 오류 발생: {} 요청경로='{}'", e.getMessage(), request.getRequestURI(), e);
        CommonErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
        return createErrorResponse(errorCode, request);
    }

    private void logException(CommonCustomException e, HttpServletRequest request) {
        String requestInfo = String.format("요청경로='%s', 코드=%s, 메시지='%s'",
                request.getRequestURI(), e.getErrorCode().getCode(), e.getMessage());

        switch (e.getErrorCode()) {
            case JWT_INVALID, JWT_EXPIRED -> log.warn("JWT 관련 오류: {}", requestInfo);
            case INTERNAL_SERVER_ERROR -> log.error("내부 서버 오류: {}", requestInfo, e);
            default -> log.warn("CommonCustomException 발생: {}", requestInfo);
        }
    }


}
