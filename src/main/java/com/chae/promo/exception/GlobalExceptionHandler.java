package com.chae.promo.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommonCustomException.class)
    protected ResponseEntity<CommonErrorResponse> handleUserCustomException(CommonCustomException e, HttpServletRequest request) {
        return createErrorResponse(e.getErrorCode(), request);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<CommonErrorResponse> handleJwtExpired(HttpServletRequest request) {
        return createErrorResponse(CommonErrorCode.JWT_EXPIRED, request);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<CommonErrorResponse> handleJwtException(HttpServletRequest request) {
        return createErrorResponse(CommonErrorCode.JWT_INVALID, request);
    }

    private ResponseEntity<CommonErrorResponse> createErrorResponse(CommonErrorCode errorCode, HttpServletRequest request) {
        return CommonErrorResponse.toResponseEntity(errorCode, request.getRequestURI());
    }
}
