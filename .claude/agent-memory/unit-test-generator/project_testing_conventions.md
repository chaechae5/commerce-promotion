---
name: Project Testing Conventions
description: Testing framework, directory layout, assertion style, and mocking patterns for the commerce-promotion backend
type: project
---

**Testing stack:** JUnit 5 (via `spring-boot-starter-test`), Mockito, AssertJ. Spring Boot 3.5.3, Java 17.

**Test directory:** `backend/src/test/java/com/chae/promo/` — mirrors the main source tree by package.

**Existing test packages:** `coupon`, `event/redis`, `order/event`, `outbox`, `auth`, `support`.

**File naming:** `<ClassName>Test.java` (e.g., `EventServiceTest.java`, `CouponRedisServiceTest.java`).

**Class-level style:**
- Pure unit tests use `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` / `@Mock` (no `@SpringBootTest`).
- Some older tests (e.g., `CouponRedisServiceTest`) manually instantiate mocks via `mock()` in `@BeforeEach` instead of using `@ExtendWith`. Both patterns exist; prefer `@ExtendWith` for new tests.
- Use `@DisplayName` on both the class and each `@Test` method (Korean display names are the project norm).

**Assertion style:** AssertJ (`assertThat`, `assertThatThrownBy`, `assertThatCode`) is preferred. `JUnit assertEquals / assertThrows` also appears in older tests.

**Mocking patterns:**
- External dependencies (Redis, DB, Kafka/Outbox) are always mocked — never real I/O in unit tests.
- `verify(mock, times(N)).method(args)` is used to confirm delegation and call counts.
- `ArgumentCaptor` is used to inspect exact arguments passed to mocks (especially for payload field assertions).
- `doThrow(...).when(mock).voidMethod(...)` pattern for void method stubs.

**Exception assertion pattern for `CommonCustomException`:**
```java
assertThatThrownBy(() -> service.method(...))
    .isInstanceOf(CommonCustomException.class)
    .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.SOME_CODE));
```
`AuthException extends CommonCustomException` so the same cast works for both.

**Redis mocking pattern for `StringRedisTemplate`:**
```java
@Mock ValueOperations<String, String> valueOperations;
when(redisTemplate.opsForValue()).thenReturn(valueOperations);
when(valueOperations.get("key")).thenReturn("value");
verify(valueOperations).set(eq("key"), eq("value"), eq(Duration.ofSeconds(ttl)));
verify(redisTemplate).delete("key");
```

**Clock mocking pattern (OutboxServiceImpl):**
- `Clock` is constructor-injected and `@Mock`ed in unit tests.
- `LocalDateTime.now(clock)` requires stubbing both `clock.instant()` and `clock.getZone()`.
- For the expected value in assertions: `LocalDateTime.now(Clock.fixed(fixedInstant, zone))`.
- Pattern confirmed in `OutboxServiceTest` (2026-03-27).

**Key auth error codes:**
- `JWT_INVALID` — null/blank/malformed token input
- `JWT_EXPIRED` — token expired (thrown by `jwtUtil.validateToken`)
- `REFRESH_TOKEN_EXPIRED` — Redis entry missing, or JWT_EXPIRED during refresh
- `INVALID_REFRESH_TOKEN` — Redis token mismatch (also triggers `redisTemplate.delete`), or JWT_INVALID during refresh
- `UNSUPPORTED_AUTH_PROVIDER` — unknown `provider` claim value in JWT
- `INTERNAL_SERVER_ERROR` — switch default branch in `validateAndExtractToken`

**StockRedisService Lua return code semantics (confirmed from source 2026-03-27):**
- `reserve()`:  `1` = success/idempotent (no exception), `-1` = PRODUCT_SOLD_OUT, `-2` = PRODUCT_STOCK_NOT_FOUND, other = `IllegalStateException`, `null` = `RuntimeException("Redis 장애")`
- `confirm()`:  `1` = success, `-4` = REDIS_STOCK_HOLD_MISSING_OR_EXPIRED, `-3` = `IllegalStateException("예약 개수 부족")`, other = `IllegalStateException`, `null` = `RuntimeException("Redis 장애")`
- `cancel()`:   `1` = success, other = warn-only (no exception), `null` = return quietly (no exception) — safe for compensating transactions
- `cancel()` uses 3 Redis keys (reserved, hold, holdIndex); `reserve()` and `confirm()` use 4 (available, reserved, hold, holdIndex)

**OrderServiceImpl.createOrder() key behaviors (confirmed 2026-03-27):**
- `ordererName` = `"비회원 " + userId`
- Call order: `productValidator` → `orderRepository.save` → `shippingInfoRepository.save` → `stockRedisService.reserve`
- Non-`CommonCustomException` from `reserve()` is re-wrapped as `RuntimeException("Redis 재고 예약 중 알 수 없는 오류가 발생했습니다.")`
- Returns `OrderResponse.OrderSummary` built directly from `Order` entity fields — no mapper involved (unlike `placeOrder()` which uses `OrderMapper`)
- `StockRedisKeyManager` is safe to instantiate with `new` in pure unit tests — `null` prefix is handled by `pfx()`, `useHashtag = false` (Java boolean default) is valid

**CouponServiceImpl Redis layering pattern (confirmed 2026-03-27):**
- `CouponRedisService` is mocked entirely in `CouponServiceTest` — its Lua-script return-code semantics are tested separately in `CouponRedisServiceTest`. Never duplicate that coverage.
- `SetOperations<String, String>` must be a separate `@Mock`; wire it via `when(redisTemplate.opsForSet()).thenReturn(setOperations)`.
- Use `lenient().when(…)` in `@BeforeEach` for Redis key stubs that not every test exercises (avoids `UnnecessaryStubbingException`).
- `DataAccessException` is the catch type for Redis system failures in `CouponServiceImpl`; test with a concrete subclass such as `QueryTimeoutException`.

**CouponIssueHandlerService exception wrapping (confirmed 2026-03-27):**
- Any exception from `findCouponByPublicId` or `saveCouponIssue` is caught and re-thrown as `RuntimeException("DB 저장 실패로 인한 재처리 요청", cause)`.
- Assert both the wrapper message and `hasCauseInstanceOf(…)` for thorough coverage.

**Payment domain notes (confirmed 2026-03-27):**
- `Order.customerId == null` → guest; validated via `ordererName.contains(userId)`
- `Order.customerId != null` → member; validated via `String.valueOf(customerId).equals(userId)`
- `prepare()` requires `OrderStatus.CREATED`; any other status → `PAYMENT_NOT_ALLOWED_STATE`
- `approve()` is idempotent: `OrderStatus.PAID` → return immediately, no PG call, no `paymentRepository` call
- `approve()` requires `OrderStatus.PENDING_PAYMENT`; other non-PAID status → `PAYMENT_NOT_ALLOWED_STATE`
- `NaverPayService.approve()` uses `equalsIgnoreCase` on `response.code()` for the SUCCESS check
- On integrity failures (amount mismatch, orderId mismatch), `integrityFailed=true` is set but the context is still `success=true` — no exception is thrown from `NaverPayService`
- `parseNaverYmdt` guard: non-null, length exactly 14, all digits; throws `IllegalArgumentException("Invalid ymdt: ...")`
- `NaverPayApproveResponse.Detail` is a compact record with 35 parameters; instantiate via the canonical constructor in tests

**Why:** team explicitly requires `@SpringBootTest`-free unit tests as stated in test requirements.

**How to apply:** Always produce `@ExtendWith(MockitoExtension.class)` tests for service-layer classes; only use `@SpringBootTest` if the user specifically requests integration tests.
