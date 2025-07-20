package com.chae.promo.coupon.entity;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CouponIssueStatus {
    ISSUED("ISSUED"),   // 발급됨
    USED("USED"),     // 사용됨
    EXPIRED("EXPIRED"); // 만료됨


    private final String value;

    /**
     * 문자열 값을 통해 CouponIssueStatus Enum을 찾아 반환합니다.
     *
     * @param value 쿠폰 발급 상태를 나타내는 문자열 값 (예: "ISSUED", "USED", "EXPIRED")
     * @return 해당 문자열 값에 대응하는 Enum 상수
     * @throws CommonCustomException 입력된 value가 null이거나,
     * 해당하는 Enum 상수를 찾을 수 없을 경우 {@code CommonErrorCode.VALIDATION_FAILED}와 함께 발생
     */
    public static CouponIssueStatus fromValue(String value) {
        if (value == null) {
            throw new CommonCustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        try {
            return CouponIssueStatus.valueOf(value); //enum 이름과 값이 같을 때 valueOf()이 효율적 O(1)
        } catch (IllegalArgumentException e ) {
            // 일치하는 Enum 상수를 찾지 못했을 때의 예외 처리
            throw new CommonCustomException(CommonErrorCode.VALIDATION_FAILED);
        }
    }
}
