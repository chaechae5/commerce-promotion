package com.chae.promo.exception;

import lombok.Getter;

@Getter
public class CommonCustomException extends RuntimeException {
    private final CommonErrorCode errorCode;

    public CommonCustomException(CommonErrorCode errorCode) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
    }
}
