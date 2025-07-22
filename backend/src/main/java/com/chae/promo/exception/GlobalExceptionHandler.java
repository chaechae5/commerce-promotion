package com.chae.promo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // AuthException을 제외한 다른 CommonCustomException들을 처리
    @ExceptionHandler(CommonCustomException.class)
    protected ResponseEntity<CommonErrorResponse> handleCustomException(CommonCustomException e, HttpServletRequest request) {

        logCustomException(e, request);
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

    //CommonCustomException 로깅 메서드
    private void logCustomException(CommonCustomException e, HttpServletRequest request) {
        String requestInfo = String.format("요청경로='%s', 코드=%s, 메시지='%s'",
                request.getRequestURI(), e.getErrorCode().getCode(), e.getMessage());

        switch (e.getErrorCode()) {
//            case JWT_INVALID, JWT_EXPIRED -> log.warn("JWT 관련 오류: {}", requestInfo);
            case INTERNAL_SERVER_ERROR -> log.error("내부 서버 오류: {}", requestInfo, e);
            default -> log.warn("CommonCustomException 발생: {}", requestInfo);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String requestInfo = String.format(
                "요청경로='%s', 코드=%s, 메시지='%s'",
                request.getRequestURI(),
                CommonErrorCode.VALIDATION_FAILED.getCode(),
                CommonErrorCode.VALIDATION_FAILED.getMessage()
        );

        log.warn("유효성 검증 실패: {}", requestInfo);
        return CommonErrorResponse.toResponseEntity(
                CommonErrorCode.VALIDATION_FAILED,
                request.getRequestURI(),
                errors // 유효성 검증 상세 오류 맵 전달
        );
    }

    /**
     * 인증관련 예외 처리
     * @param e
     * @param request
     * @return
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<CommonErrorResponse> handleAuthException(AuthException e, HttpServletRequest request) {

        log.warn("인증 오류 발생: 요청경로='{}', 코드={}, 메시지='{}'",
                request.getRequestURI(), e.getErrorCode(), e.getMessage());

        return createErrorResponse(e.getErrorCode(), request);
    }

}
