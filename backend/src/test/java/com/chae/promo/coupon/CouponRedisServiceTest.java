package com.chae.promo.coupon;

import com.chae.promo.coupon.dto.CouponRedisRequest;
import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.service.redis.CouponRedisKeyManager;
import com.chae.promo.coupon.service.redis.CouponRedisService;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("CouponRedisService 테스트")
public class CouponRedisServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private CouponRedisService couponRedisService;
    private CouponRedisKeyManager couponRedisKeyManager;

    private static final String TEST_COUPON_PUBLIC_ID = "TEST_PUBLIC_ID_001";
    private static final String TEST_COUPON_CODE = "TEST_CODE_A";
    private static final String TEST_USER_ID_PREFIX = "user_";
    private static final int INITIAL_STOCK = 10;
    private static final int TTL_SECONDS = 600; // 10분

    private String stockKey;
    private String couponIssuedUserSetKey;
    private String couponTtlKey;


    @BeforeEach
    void setUp() {
        // Mock 객체 초기화
        stringRedisTemplate = mock(StringRedisTemplate.class);
        couponRedisKeyManager = new CouponRedisKeyManager();
        couponRedisService = new CouponRedisService(stringRedisTemplate);

        //key 생성
        stockKey = couponRedisKeyManager.getCouponStockKey(TEST_COUPON_PUBLIC_ID, TEST_COUPON_CODE);
        couponIssuedUserSetKey = couponRedisKeyManager.getCouponIssuedUserSetKey(TEST_COUPON_PUBLIC_ID, TEST_COUPON_CODE);
        couponTtlKey = couponRedisKeyManager.getCouponTtlKey(TEST_COUPON_PUBLIC_ID, TEST_COUPON_CODE);

    }

    private CouponRedisRequest createCouponRedisRequest(String userId) {
        // 테스트용 Coupon 객체 생성 (Coupon 도메인 객체 필요)
        Coupon testCoupon = Coupon.builder()
                .id(1L)
                .publicId(TEST_COUPON_PUBLIC_ID)
                .code(TEST_COUPON_CODE)
                .name("Test Coupon")
                .totalQuantity(INITIAL_STOCK)
                .build();

        // CouponRedisRequest 객체 생성
        return CouponRedisRequest.builder()
                .couponStockKey(stockKey)
                .userCouponSetKey(couponRedisKeyManager.getUserCouponSetKey(userId)) // 사용자별 키는 동적으로 생성
                .couponTtlKey(couponTtlKey)
                .couponIssuedUserSetKey(couponIssuedUserSetKey)
                .userId(userId)
                .coupon(testCoupon)
                .ttlSeconds(TTL_SECONDS)
                .build();
    }

    @Test
    @DisplayName("쿠폰 발급 성공 테스트")
    void issueCouponAtomically_success() {
        String userId = TEST_USER_ID_PREFIX + "1";
        CouponRedisRequest request = createCouponRedisRequest(userId);

        // 쿠폰 발급 시도
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(1L);

        couponRedisService.issueCouponAtomically(request);

        //then
        // Redis 스크립트 실행이 성공적으로 호출되었는지 검증
        verify(stringRedisTemplate, times(1)).execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class), any(String.class));

        // execute 메서드에 전달된 인자들을 캡처하여 상세 검증
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String[]> argsCaptor = ArgumentCaptor.forClass(String[].class);

        verify(stringRedisTemplate).execute(any(), keysCaptor.capture(), (Object[]) argsCaptor.capture());


        assertThat(keysCaptor.getValue()).containsExactly(
                request.getCouponStockKey(),
                request.getUserCouponSetKey(),
                request.getCouponTtlKey(),
                request.getCouponIssuedUserSetKey()
        );
        assertThat(argsCaptor.getValue()).containsExactly(
                request.getUserId(),
                request.getCoupon().getPublicId(),
                String.valueOf(request.getTtlSeconds())
        );

    }

    @Test
    @DisplayName("쿠폰 발급 실패: 재고 소진 시 CommonCustomException 발생")
    void issueCouponAtomically_fail_stockOut() {
        // Given
        String userId = TEST_USER_ID_PREFIX + "1";
        CouponRedisRequest request = createCouponRedisRequest(userId);

        // Mock 객체의 execute 메서드가 2L을 반환하도록 설정 (재고 소진)
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(2L);

        // When & Then
        assertThatThrownBy(() -> couponRedisService.issueCouponAtomically(request))
                .isInstanceOf(CommonCustomException.class)
                .hasMessageContaining(CommonErrorCode.COUPON_SOLD_OUT.getMessage());

    }

    @Test
    @DisplayName("쿠폰 발급 실패: 중복 발급 시 CommonCustomException 발생")
    void issueCouponAtomically_fail_duplicate() {
        // Given
        String userId = TEST_USER_ID_PREFIX + "1";
        CouponRedisRequest request = createCouponRedisRequest(userId);

        // Mock 객체의 execute 메서드가 3L을 반환하도록 설정 (중복 발급)
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(3L);

        // When & Then
        assertThatThrownBy(() -> couponRedisService.issueCouponAtomically(request))
                .isInstanceOf(CommonCustomException.class)
                .hasMessageContaining(CommonErrorCode.COUPON_ALREADY_ISSUED.getMessage());

    }

    @Test
    @DisplayName("쿠폰 발급 실패: 쿠폰 기간 만료 시 CommonCustomException 발생")
    void issueCouponAtomically_fail_ttlExpired() {
        // Given
        String userId = TEST_USER_ID_PREFIX + "1";
        CouponRedisRequest request = createCouponRedisRequest(userId);

        // Mock 객체의 execute 메서드가 4L을 반환하도록 설정 (쿠폰 만료)
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(4L);

        // When & Then
        assertThatThrownBy(() -> couponRedisService.issueCouponAtomically(request))
                .isInstanceOf(CommonCustomException.class)
                .hasMessageContaining(CommonErrorCode.COUPON_EXPIRED.getMessage());

    }

    @Test
    @DisplayName("다중 스레드 환경에서 쿠폰 동시 발급 테스트 - Mock 버전")
    void issueCouponAtomically_concurrentIssue() throws InterruptedException {
        // Given
        int numberOfThreads = 100; // 동시 요청 수
        int successCount = 10;

        AtomicInteger callCount = new AtomicInteger(0);
        AtomicInteger successfulIssues = new AtomicInteger(0);
        AtomicInteger failedIssues = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // Mock이 호출될 때마다 카운터 증가
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    int currentCall = callCount.incrementAndGet();
                    return currentCall <= successCount ? 1L : 2L; // 처음 10번은 성공, 나머지는 실패
                });


        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final String currentUserId = TEST_USER_ID_PREFIX + i;
            final CouponRedisRequest request = createCouponRedisRequest(currentUserId);

            executorService.submit(() -> {
                try {
                    couponRedisService.issueCouponAtomically(request);
                    successfulIssues.incrementAndGet();
                } catch (CommonCustomException e) {
                    if (e.getErrorCode() == CommonErrorCode.COUPON_SOLD_OUT) {
                        failedIssues.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        // 모든 스레드가 완료될 때까지 대기
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();

        // Then
        assertThat(successfulIssues.get()).isEqualTo(successCount);
        assertThat(failedIssues.get()).isEqualTo(numberOfThreads - successCount);
        // execute 메서드가 총 100번 호출되었는지 검증
        verify(stringRedisTemplate, times(numberOfThreads))
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));

        // ArgumentCaptor를 사용하여 execute 메서드가 호출된 모든 인자들을 캡처
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String[]> argsCaptor = ArgumentCaptor.forClass(String[].class);

        // 모든 호출에 대해 인자 캡처
        verify(stringRedisTemplate, atLeast(1)).execute(any(), keysCaptor.capture(), (Object[]) argsCaptor.capture());

        // 첫 번째 키(재고 키)는 모든 호출에서 동일해야 함
        assertThat(keysCaptor.getAllValues())
                .allSatisfy(keys -> assertThat(keys.get(0)).isEqualTo(stockKey));

        // 각 호출의 userId는 고유해야 함
        assertThat(argsCaptor.getAllValues())
                .extracting(args -> args[0])
                .containsExactlyInAnyOrder(
                        IntStream.range(0, numberOfThreads)
                                .mapToObj(i -> TEST_USER_ID_PREFIX + i)
                                .toArray(String[]::new)
                );

    }
}
