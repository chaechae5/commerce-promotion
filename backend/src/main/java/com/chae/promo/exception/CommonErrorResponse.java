package com.chae.promo.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class CommonErrorResponse {
    private int status;
    private String errorCode;
    private String message;
    private String path;
    private long timestamp;
    private Map<String, String> details;


    public static ResponseEntity<CommonErrorResponse> toResponseEntity(CommonErrorCode errorCode, String path) {


        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(CommonErrorResponse.builder()
                        .status(errorCode.getHttpStatus().value())
                        .errorCode(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .path(path)
                        .timestamp(System.currentTimeMillis())
                        .build());
    }

    /**
     * 유효성 검증 오류를 인자로 받아 response 생성
     * @param errorCode 유효성 검증 관련 CommonErrorCode
     * @param path 요청 URI
     * @param validationErrors 필드별 유효성 검증 오류 메시지 맵
     * @return ResponseEntity<CommonErrorResponse>
     */
    public static ResponseEntity<CommonErrorResponse> toResponseEntity(
            CommonErrorCode errorCode,
            String path,
            Map<String, String> validationErrors) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonErrorResponse.builder()
                        .status(errorCode.getHttpStatus().value())
                        .errorCode(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .path(path)
                        .details(validationErrors)
                        .build());
    }
}
