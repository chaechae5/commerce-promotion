package com.chae.promo.auth.domain;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenType {

    ANONYMOUS("anonymous");

    private final String value;

    /**
     * 문자열 값을 통해 TokenType Enum을 찾아 반환
     *
     * @param value JWT Claims의 subject 값 (예: "anonymous", "user")
     * @return 해당 문자열 값에 대응하는 enum
     * @throws IllegalArgumentException 유효하지 않은 subject 값일 경우
     */
    public static TokenType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("");
        }

        for (TokenType type : TokenType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new CommonCustomException(CommonErrorCode.JWT_INVALID);
    }

}
