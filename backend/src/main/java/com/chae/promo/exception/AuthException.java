package com.chae.promo.exception;

public class AuthException extends CommonCustomException{
    public AuthException(CommonErrorCode errorCode) {
        super(errorCode);
    }
}
