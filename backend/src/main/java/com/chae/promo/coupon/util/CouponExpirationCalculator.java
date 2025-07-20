package com.chae.promo.coupon.util;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class CouponExpirationCalculator {

    /**
     * 쿠폰의 속성에 따라 최종 만료 일시를 계산
     * 계산 우선순위
     * 1. 만료일 (expireDate)
     * 2. 유효일수 (validDays)
     * 3. 기본값 (7일) - 만료일, 유효일수 모두 없는 경우
     *
     * @param coupon 만료일을 계산할 쿠폰 객체
     * @param now 기준시각
     * @return 최종 계산된 만료 일시 (LocalDateTime)
     */
    public LocalDateTime calculateExpiration(Coupon coupon, LocalDateTime now) {
        if (coupon.getExpireDate() != null) {
            return coupon.getExpireDate();
        }

        if (coupon.getValidDays() != null && coupon.getValidDays() > 0) {
            return now.plusDays(coupon.getValidDays());
        } else {
            log.warn("쿠폰 {}의 유효 일수 및 만료일이 설정되지 않았습니다. 기본 7일 유효 기간 적용.", coupon.getCode());
            return now.plusDays(7);
        }
    }

    /**
     * 기준 시점(now)과 만료 시점(expireAt) 사이의 남은 시간을 초(TTL) 단위 계산
     *
     * @param expireAt 만료 시점
     * @param now      계산의 기준이 되는 현재 또는 기준 시점
     * @return 남은 시간(초). 0초일 경우 최소 1초를 보장하여 반환
     * @throws CommonCustomException 만료 시점이 기준 시점보다 과거일 경우 발생
     */
    public long calculateTtlSeconds(LocalDateTime expireAt, LocalDateTime now) {
        Duration duration = Duration.between(now, expireAt);
        long ttl = duration.getSeconds();
        if (ttl < 0) {
            log.warn("쿠폰 만료 TTL < 0. 쿠폰이 이미 만료되었습니다.");
            throw new CommonCustomException(CommonErrorCode.COUPON_EXPIRED);
        }
        if (ttl == 0) {
            log.info("쿠폰 만료 TTL이 0입니다. 최소 TTL 1초로 설정합니다.");
            return 1;
        }
        return ttl;
    }
}
