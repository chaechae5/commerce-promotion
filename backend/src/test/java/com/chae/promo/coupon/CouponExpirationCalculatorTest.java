package com.chae.promo.coupon;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.util.CouponExpirationCalculator;
import com.chae.promo.exception.CommonCustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CouponExpirationCalculatorTest {

    private CouponExpirationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CouponExpirationCalculator();
    }

    @Test
    @DisplayName("만료일이 있으면 해당 날짜를 반환")
    void testCalculateExpiration_withExpireDate(){

        Coupon coupon = mock(Coupon.class);
        LocalDateTime expireDate = LocalDateTime.now().plusDays(3);
        when(coupon.getExpireDate()).thenReturn(expireDate);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime result = calculator.calculateExpiration(coupon, now);

        assertThat(result).isEqualTo(expireDate);
    }

    @Test
    @DisplayName("쿠폰 유효일수가 있으면 현재 날짜에 쿠폰 유효일 더해서 반환")
    void testCalculateExpiration_withValidDays() {

        Coupon coupon = mock(Coupon.class);
        when(coupon.getExpireDate()).thenReturn(null);
        when(coupon.getValidDays()).thenReturn(5);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime result = calculator.calculateExpiration(coupon, now);

        assertThat(result).isEqualTo(now.plusDays(5));

    }

    @Test
    @DisplayName("만료일과 유효 일수 모두 없으면 기본값(7일) 적용")
    void testCalculateExpiration_withDefaultValidDays() {

        Coupon coupon = mock(Coupon.class);
        when(coupon.getExpireDate()).thenReturn(null);
        when(coupon.getValidDays()).thenReturn(null);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime result = calculator.calculateExpiration(coupon, now);

        assertThat(result).isEqualTo(now.plusDays(7));
    }

    @Test
    @DisplayName("만료일이 미래이면 남은 시간을 초로 반환")
    void testCalculateTtlSeconds_valid() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime expireAt = now.plusSeconds(10);
        long ttl = calculator.calculateTtlSeconds(expireAt, now);

        assertThat(ttl).isEqualTo(10L);
    }

    @Test
    @DisplayName("만료일이 이미 지났으면 예외 발생")
    void testCalculateTtlSeconds_expired() {
        LocalDateTime expireAt = LocalDateTime.now().minusSeconds(10);
        assertThrows(CommonCustomException.class, () -> calculator.calculateTtlSeconds(expireAt, LocalDateTime.now()));
    }

    @Test
    @DisplayName("남은 시간이 0초이면 1초 반환")
    void testCalculateTtlSeconds_zero() {
        LocalDateTime expireAt = LocalDateTime.now();
        long ttl = calculator.calculateTtlSeconds(expireAt, expireAt);

        assertThat(ttl).isEqualTo(1L);
    }
}
