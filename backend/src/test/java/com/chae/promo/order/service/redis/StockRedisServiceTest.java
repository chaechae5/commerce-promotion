package com.chae.promo.order.service.redis;

/**
 * Tested: StockRedisService
 *
 * reserve():
 *   - return 1  → 성공 (또는 멱등) — no exception
 *   - return -1 → 재고 부족       — PRODUCT_SOLD_OUT
 *   - return -2 → 재고 키 없음    — PRODUCT_STOCK_NOT_FOUND
 *   - other     → 예상치 못한 코드 — IllegalStateException
 *   - null      → Redis 장애      — RuntimeException
 *
 * confirm():
 *   - return 1  → 확정 성공       — no exception
 *   - return -4 → hold 없음(만료) — REDIS_STOCK_HOLD_MISSING_OR_EXPIRED
 *   - return -3 → 예약 개수 부족  — IllegalStateException
 *   - other     → 예상치 못한 코드 — IllegalStateException
 *   - null      → Redis 장애      — RuntimeException
 *
 * cancel():
 *   - return 1  → 취소 성공       — no exception
 *   - return 0  → 예상치 못한 코드 — no exception (warn only)
 *   - null      → Redis 장애      — no exception (return quietly)
 */

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockRedisService 단위 테스트")
class StockRedisServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScriptCatalog scripts;

    // Real key manager — no Spring context needed (prefix/hashtag fields are not @Value-injected in tests,
    // so defaults apply: prefix blank, useHashtag = false via primitive default)
    private StockRedisKeyManager key;

    private StockRedisService stockRedisService;

    private static final String SKU      = "P-TEST-001";
    private static final String ORDER_ID = "order-uuid-9999";
    private static final long   QUANTITY = 3L;
    private static final long   TTL_SEC  = 600L;

    @BeforeEach
    void setUp() {
        key = new StockRedisKeyManager();
        stockRedisService = new StockRedisService(stringRedisTemplate, scripts, key);

        // Provide non-null script stubs (content doesn't matter for unit tests — Lua is not executed)
        // lenient: each nested class only uses one of the three scripts
        lenient().when(scripts.reserveStock()).thenReturn(new DefaultRedisScript<>("return 1", Long.class));
        lenient().when(scripts.confirmStock()).thenReturn(new DefaultRedisScript<>("return 1", Long.class));
        lenient().when(scripts.cancelStock()).thenReturn(new DefaultRedisScript<>("return 1", Long.class));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  reserve()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("성공 (return 1) — 예외 없이 정상 완료")
        void reserve_success_returnsOne_noException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .willReturn(1L);

            // Act & Assert
            assertThatNoException()
                    .isThrownBy(() -> stockRedisService.reserve(SKU, ORDER_ID, QUANTITY, TTL_SEC));

            verify(stringRedisTemplate, times(1))
                    .execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("재고 부족 (return -1) — PRODUCT_SOLD_OUT 예외 발생")
        void reserve_soldOut_returnsMinusOne_throwsProductSoldOut() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .willReturn(-1L);

            // Act & Assert
            assertThatExceptionOfType(CommonCustomException.class)
                    .isThrownBy(() -> stockRedisService.reserve(SKU, ORDER_ID, QUANTITY, TTL_SEC))
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.PRODUCT_SOLD_OUT));
        }

        @Test
        @DisplayName("상품(재고 키) 없음 (return -2) — PRODUCT_STOCK_NOT_FOUND 예외 발생")
        void reserve_stockKeyNotFound_returnsMinusTwo_throwsProductStockNotFound() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .willReturn(-2L);

            // Act & Assert
            assertThatExceptionOfType(CommonCustomException.class)
                    .isThrownBy(() -> stockRedisService.reserve(SKU, ORDER_ID, QUANTITY, TTL_SEC))
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.PRODUCT_STOCK_NOT_FOUND));
        }

        @Test
        @DisplayName("예상치 못한 반환 코드 — IllegalStateException 발생")
        void reserve_unexpectedCode_throwsIllegalStateException() {
            // Arrange — any code not in {1, -1, -2} falls to default branch
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .willReturn(99L);

            // Act & Assert
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> stockRedisService.reserve(SKU, ORDER_ID, QUANTITY, TTL_SEC))
                    .withMessageContaining("Reserve unexpected: 99");
        }

        @Test
        @DisplayName("Redis null 반환 — RuntimeException(Redis 장애) 발생")
        void reserve_nullResult_throwsRuntimeException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .willReturn(null);

            // Act & Assert
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> stockRedisService.reserve(SKU, ORDER_ID, QUANTITY, TTL_SEC))
                    .withMessageContaining("Redis 장애");
        }

        @Test
        @DisplayName("execute 호출 시 keys 목록은 available, reserved, hold, holdIndex 순서로 전달된다")
        void reserve_keysPassedInCorrectOrder() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .willReturn(1L);

            // Act
            stockRedisService.reserve(SKU, ORDER_ID, QUANTITY, TTL_SEC);

            // Assert — verify the exact key list passed
            List<String> expectedKeys = List.of(
                    key.available(SKU),
                    key.reserved(SKU),
                    key.hold(SKU, ORDER_ID),
                    key.holdIndex(SKU)
            );

            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    org.mockito.ArgumentMatchers.eq(expectedKeys),
                    anyString(), anyString(), anyString()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  confirm()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("확정 성공 (return 1) — 예외 없이 정상 완료")
        void confirm_success_returnsOne_noException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(1L);

            // Act & Assert
            assertThatNoException()
                    .isThrownBy(() -> stockRedisService.confirm(SKU, ORDER_ID));

            verify(stringRedisTemplate, times(1))
                    .execute(any(DefaultRedisScript.class), anyList());
        }

        @Test
        @DisplayName("hold 없음 만료 (return -4) — REDIS_STOCK_HOLD_MISSING_OR_EXPIRED 예외 발생")
        void confirm_holdMissingOrExpired_returnsMinusFour_throwsException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(-4L);

            // Act & Assert
            assertThatExceptionOfType(CommonCustomException.class)
                    .isThrownBy(() -> stockRedisService.confirm(SKU, ORDER_ID))
                    .satisfies(ex -> assertThat(ex.getErrorCode())
                            .isEqualTo(CommonErrorCode.REDIS_STOCK_HOLD_MISSING_OR_EXPIRED));
        }

        @Test
        @DisplayName("예약 개수 부족 (return -3) — IllegalStateException 발생")
        void confirm_reservedInsufficient_returnsMinusThree_throwsIllegalStateException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(-3L);

            // Act & Assert
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> stockRedisService.confirm(SKU, ORDER_ID))
                    .withMessageContaining("예약 개수 부족");
        }

        @Test
        @DisplayName("예상치 못한 반환 코드 — IllegalStateException 발생")
        void confirm_unexpectedCode_throwsIllegalStateException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(77L);

            // Act & Assert
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> stockRedisService.confirm(SKU, ORDER_ID))
                    .withMessageContaining("예상치 못한 오류");
        }

        @Test
        @DisplayName("Redis null 반환 — RuntimeException(Redis 장애) 발생")
        void confirm_nullResult_throwsRuntimeException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(null);

            // Act & Assert
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> stockRedisService.confirm(SKU, ORDER_ID))
                    .withMessageContaining("Redis 장애");
        }

        @Test
        @DisplayName("execute 호출 시 keys 목록은 available, reserved, hold, holdIndex 순서로 전달된다")
        void confirm_keysPassedInCorrectOrder() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(1L);

            // Act
            stockRedisService.confirm(SKU, ORDER_ID);

            // Assert
            List<String> expectedKeys = List.of(
                    key.available(SKU),
                    key.reserved(SKU),
                    key.hold(SKU, ORDER_ID),
                    key.holdIndex(SKU)
            );

            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    org.mockito.ArgumentMatchers.eq(expectedKeys)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  cancel()
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("취소 성공 (return 1) — 예외 없이 정상 완료")
        void cancel_success_returnsOne_noException() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(1L);

            // Act & Assert
            assertThatNoException()
                    .isThrownBy(() -> stockRedisService.cancel(SKU, ORDER_ID));

            verify(stringRedisTemplate, times(1))
                    .execute(any(DefaultRedisScript.class), anyList());
        }

        @Test
        @DisplayName("예상치 못한 반환 코드 (return 0) — 예외 없이 경고 로그만 출력하고 완료")
        void cancel_unexpectedCode_returnsZero_noException() {
            // Arrange — implementation only logs warn, does not throw
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(0L);

            // Act & Assert
            assertThatNoException()
                    .isThrownBy(() -> stockRedisService.cancel(SKU, ORDER_ID));
        }

        @Test
        @DisplayName("Redis null 반환 — 예외 없이 조용히 반환 (보상 로직 안전성 보장)")
        void cancel_nullResult_returnsQuietly_noException() {
            // Arrange — null is handled by returning early (no exception, unlike reserve/confirm)
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(null);

            // Act & Assert
            assertThatNoException()
                    .isThrownBy(() -> stockRedisService.cancel(SKU, ORDER_ID));
        }

        @Test
        @DisplayName("execute 호출 시 keys 목록은 reserved, hold, holdIndex 순서로 전달된다")
        void cancel_keysPassedInCorrectOrder() {
            // Arrange
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                    .willReturn(1L);

            // Act
            stockRedisService.cancel(SKU, ORDER_ID);

            // Assert — cancel uses 3 keys: reserved, hold, holdIndex (no 'available' key)
            List<String> expectedKeys = List.of(
                    key.reserved(SKU),
                    key.hold(SKU, ORDER_ID),
                    key.holdIndex(SKU)
            );

            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    org.mockito.ArgumentMatchers.eq(expectedKeys)
            );
        }
    }
}
