package com.chae.promo.auth.domain;

import com.chae.promo.exception.AuthException;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.security.resolver.AnonymousUserPrincipalResolver;
import com.chae.promo.security.resolver.UserPrincipalResolver;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthProviderType {

    ANONYMOUS("ANONYMOUS", new AnonymousUserPrincipalResolver());

    private final String value;
    private final UserPrincipalResolver resolver;

    /**
     * 문자열 값을 통해 AuthProviderType Enum을 찾아 반환합니다.
     *
     * @param value JWT Claims의 subject 값 (예: "anonymous", "refresh_token")
     * @return 해당 문자열 값에 대응하는 Enum 상수
     * @throws CommonCustomException 입력된 value가 null이거나,
     * 해당하는 Enum 상수를 찾을 수 없을 경우 {@code CommonErrorCode.VALIDATION_FAILED}와 함께 발생
     */
    public static AuthProviderType fromValue(String value) {
        if (value == null) {
            throw new CommonCustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        try {
            return AuthProviderType.valueOf(value); //enum 이름과 값이 같을 때 valueOf()이 효율적 O(1)
        } catch (IllegalArgumentException e ) {
            // 일치하는 Enum 상수를 찾지 못했을 때의 예외 처리
            throw new AuthException(CommonErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
    }

    public UserPrincipalResolver getResolver() {
        return resolver;
    }
}
