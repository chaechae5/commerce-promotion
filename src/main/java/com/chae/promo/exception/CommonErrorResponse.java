package com.chae.promo.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
@Builder
@AllArgsConstructor
public class CommonErrorResponse {
    private int status;
    private String errorCode;
    private String message;
    private String path;
    private long timestamp;

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
}
