package com.chae.promo.coupon.service;

// Tested: CouponServiceImpl
//   - issueCoupon(): 성공, Redis 재고 없음(-1), 중복 발급(-2), 만료된 쿠폰(-3), DataAccessException(Redis 장애)
//   - getAll(): 전체 쿠폰 목록 반환, 쿠폰이 없는 경우
//   - getMyCoupons(): Redis 캐시 히트, Redis 캐시 미스(DB fallback), DB도 비어있는 경우

import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.event.CouponEventPublisher;
import com.chae.promo.coupon.event.CouponIssuedEvent;
import com.chae.promo.coupon.mapper.CouponMapper;
import com.chae.promo.coupon.repository.CouponIssueRepository;
import com.chae.promo.coupon.repository.CouponRepository;
import com.chae.promo.coupon.service.redis.CouponRedisKeyManager;
import com.chae.promo.coupon.service.redis.CouponRedisService;
import com.chae.promo.coupon.util.CouponExpirationCalculator;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponServiceImpl 테스트")
class CouponServiceTest {

    @InjectMocks
    private CouponServiceImpl couponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssueRepository couponIssueRepository;

    @Mock
    private CouponRedisService couponRedisService;

    @Mock
    private CouponExpirationCalculator couponExpirationCalculator;

    @Mock
    private CouponRedisKeyManager couponRedisKeyManager;

    @Mock
    private CouponEventPublisher couponEventPublisher;

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    // -------- 공통 픽스처 --------
    private static final String USER_ID     = "user_001";
    private static final String COUPON_ID   = "coupon_pub_001";
    private static final String COUPON_CODE = "PROMO2024";

    private static final String STOCK_KEY        = "coupon:stock:" + COUPON_ID + ":" + COUPON_CODE;
    private static final String USER_COUPON_KEY  = "user:" + USER_ID + ":coupon";
    private static final String TTL_KEY          = "coupon:ttl:" + COUPON_ID + ":" + COUPON_CODE;
    private static final String ISSUED_USERS_KEY = "coupon:issued_users:" + COUPON_ID + ":" + COUPON_CODE;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        testCoupon = Coupon.builder()
                .id(1L)
                .publicId(COUPON_ID)
                .code(COUPON_CODE)
                .name("테스트 쿠폰")
                .description("테스트 설명")
                .totalQuantity(100)
                .validDays(7)
                .build();

        // 공통 Redis Key 스텁 — 개별 테스트에서 필요한 것만 사용됨
        lenient().when(couponRedisKeyManager.getCouponStockKey(COUPON_ID, COUPON_CODE)).thenReturn(STOCK_KEY);
        lenient().when(couponRedisKeyManager.getUserCouponSetKey(USER_ID)).thenReturn(USER_COUPON_KEY);
        lenient().when(couponRedisKeyManager.getCouponTtlKey(COUPON_ID, COUPON_CODE)).thenReturn(TTL_KEY);
        lenient().when(couponRedisKeyManager.getCouponIssuedUserSetKey(COUPON_ID, COUPON_CODE)).thenReturn(ISSUED_USERS_KEY);
    }

    // ====================================================================
    //  issueCoupon — 성공
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: 정상 발급 성공 시 CouponResponse.Issue를 반환한다")
    void issueCoupon_success_returnsIssueResponse() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(604800L);

        // Redis 재고 키와 TTL 키 모두 이미 존재한다고 가정
        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(true);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(true);

        doNothing().when(couponRedisService).issueCouponAtomically(any());
        doNothing().when(couponEventPublisher).publishCouponIssued(any(CouponIssuedEvent.class));

        // Act
        CouponResponse.Issue result = couponService.issueCoupon(USER_ID, COUPON_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(COUPON_CODE);
        assertThat(result.getName()).isEqualTo("테스트 쿠폰");
        assertThat(result.getStatus()).isEqualTo(CouponIssueStatus.ISSUED.getValue());
        assertThat(result.getExpireAt()).isEqualTo(expireAt);
        assertThat(result.getCouponIssueId()).isNotBlank();

        verify(couponEventPublisher).publishCouponIssued(any(CouponIssuedEvent.class));
    }

    @Test
    @DisplayName("issueCoupon: Redis에 재고 키가 없을 때 DB에서 재고를 초기화한 후 발급한다")
    void issueCoupon_success_initializesStockFromDB_whenStockKeyMissing() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(604800L);

        // 재고 키 없음 → DB 초기화 경로 진입
        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(false);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(true);
        when(couponIssueRepository.countByCouponIdAndStatus(1L, CouponIssueStatus.ISSUED)).thenReturn(30L);

        doNothing().when(couponRedisService).setCouponStock(eq(STOCK_KEY), eq(70L));
        doNothing().when(couponRedisService).issueCouponAtomically(any());
        doNothing().when(couponEventPublisher).publishCouponIssued(any());

        // Act
        CouponResponse.Issue result = couponService.issueCoupon(USER_ID, COUPON_ID);

        // Assert
        assertThat(result).isNotNull();
        verify(couponRedisService).setCouponStock(STOCK_KEY, 70L);
    }

    @Test
    @DisplayName("issueCoupon: Redis에 TTL 키가 없을 때 TTL 키를 생성한 후 발급한다")
    void issueCoupon_success_setsTtlKey_whenTtlKeyMissing() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);
        long ttlSeconds = 604800L;

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(ttlSeconds);

        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(true);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(false);

        doNothing().when(couponRedisService).setCouponTtlKey(TTL_KEY, ttlSeconds);
        doNothing().when(couponRedisService).issueCouponAtomically(any());
        doNothing().when(couponEventPublisher).publishCouponIssued(any());

        // Act
        CouponResponse.Issue result = couponService.issueCoupon(USER_ID, COUPON_ID);

        // Assert
        assertThat(result).isNotNull();
        verify(couponRedisService).setCouponTtlKey(TTL_KEY, ttlSeconds);
    }

    // ====================================================================
    //  issueCoupon — 재고 소진 (-1 / COUPON_SOLD_OUT)
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: Redis 재고 소진 시 CommonCustomException(COUPON_SOLD_OUT)이 발생한다")
    void issueCoupon_fail_couponSoldOut_throwsCommonCustomException() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(604800L);

        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(true);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(true);

        doThrow(new CommonCustomException(CommonErrorCode.COUPON_SOLD_OUT))
                .when(couponRedisService).issueCouponAtomically(any());

        // Act & Assert
        assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                .isInstanceOf(CommonCustomException.class)
                .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.COUPON_SOLD_OUT));

        verify(couponEventPublisher, never()).publishCouponIssued(any());
    }

    // ====================================================================
    //  issueCoupon — 중복 발급 (-2 / COUPON_ALREADY_ISSUED)
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: 동일 사용자 중복 발급 시 CommonCustomException(COUPON_ALREADY_ISSUED)이 발생한다")
    void issueCoupon_fail_alreadyIssued_throwsCommonCustomException() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(604800L);

        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(true);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(true);

        doThrow(new CommonCustomException(CommonErrorCode.COUPON_ALREADY_ISSUED))
                .when(couponRedisService).issueCouponAtomically(any());

        // Act & Assert
        assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                .isInstanceOf(CommonCustomException.class)
                .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.COUPON_ALREADY_ISSUED));

        verify(couponEventPublisher, never()).publishCouponIssued(any());
    }

    // ====================================================================
    //  issueCoupon — 만료된 쿠폰 (-3 / COUPON_EXPIRED)
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: 만료 기간이 지난 쿠폰은 CommonCustomException(COUPON_EXPIRED)이 발생한다")
    void issueCoupon_fail_expired_throwsCommonCustomException() {
        // Arrange — calculateTtlSeconds 가 COUPON_EXPIRED 예외를 던지도록 설정
        LocalDateTime expireAt = LocalDateTime.now().minusDays(1);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenThrow(new CommonCustomException(CommonErrorCode.COUPON_EXPIRED));

        // Act & Assert
        assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                .isInstanceOf(CommonCustomException.class)
                .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.COUPON_EXPIRED));

        verify(couponRedisService, never()).issueCouponAtomically(any());
        verify(couponEventPublisher, never()).publishCouponIssued(any());
    }

    // ====================================================================
    //  issueCoupon — Redis 시스템 장애 (DataAccessException)
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: Redis DataAccessException 발생 시 RuntimeException이 전파된다")
    void issueCoupon_fail_redisDataAccessException_throwsRuntimeException() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(604800L);

        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(true);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(true);

        // DataAccessException 의 구체 구현체 사용
        doThrow(new QueryTimeoutException("connection timeout"))
                .when(couponRedisService).issueCouponAtomically(any());

        // Act & Assert
        assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis 장애로 쿠폰 발급 불가");

        verify(couponEventPublisher, never()).publishCouponIssued(any());
    }

    // ====================================================================
    //  issueCoupon — 쿠폰 미존재 (COUPON_NOT_FOUND)
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: 존재하지 않는 쿠폰 ID로 발급 시 CommonCustomException(COUPON_NOT_FOUND)이 발생한다")
    void issueCoupon_fail_couponNotFound_throwsCommonCustomException() {
        // Arrange
        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID))
                .thenThrow(new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND));

        // Act & Assert
        assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                .isInstanceOf(CommonCustomException.class)
                .satisfies(ex -> assertThat(((CommonCustomException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.COUPON_NOT_FOUND));

        verify(couponRedisService, never()).issueCouponAtomically(any());
    }

    // ====================================================================
    //  issueCoupon — CouponIssuedEvent 발행 인자 검증
    // ====================================================================

    @Test
    @DisplayName("issueCoupon: 발급 성공 시 CouponEventPublisher에 올바른 userId와 couponPublicId가 전달된다")
    void issueCoupon_success_publishesEventWithCorrectFields() {
        // Arrange
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7);

        when(couponRepository.getCouponByPublicIdOrThrow(COUPON_ID)).thenReturn(testCoupon);
        when(couponExpirationCalculator.calculateExpiration(eq(testCoupon), any(LocalDateTime.class)))
                .thenReturn(expireAt);
        when(couponExpirationCalculator.calculateTtlSeconds(eq(expireAt), any(LocalDateTime.class)))
                .thenReturn(604800L);
        when(couponRedisService.hasKey(STOCK_KEY)).thenReturn(true);
        when(couponRedisService.hasKey(TTL_KEY)).thenReturn(true);
        doNothing().when(couponRedisService).issueCouponAtomically(any());

        ArgumentCaptor<CouponIssuedEvent> eventCaptor = ArgumentCaptor.forClass(CouponIssuedEvent.class);

        // Act
        couponService.issueCoupon(USER_ID, COUPON_ID);

        // Assert
        verify(couponEventPublisher).publishCouponIssued(eventCaptor.capture());
        CouponIssuedEvent captured = eventCaptor.getValue();
        assertThat(captured.getUserId()).isEqualTo(USER_ID);
        assertThat(captured.getCouponPublicId()).isEqualTo(COUPON_ID);
        assertThat(captured.getCouponCode()).isEqualTo(COUPON_CODE);
        assertThat(captured.getExpiredAt()).isEqualTo(expireAt);
    }

    // ====================================================================
    //  getAll
    // ====================================================================

    @Test
    @DisplayName("getAll: 쿠폰 목록이 있을 때 CouponResponse.Info 리스트를 반환한다")
    void getAll_returnsCouponInfoList() {
        // Arrange
        List<Coupon> coupons = List.of(testCoupon);
        List<CouponResponse.Info> expected = List.of(
                CouponResponse.Info.builder()
                        .couponId(COUPON_ID)
                        .code(COUPON_CODE)
                        .name("테스트 쿠폰")
                        .build()
        );

        when(couponRepository.findAll()).thenReturn(coupons);
        when(couponMapper.toInfoListFromCoupons(coupons)).thenReturn(expected);

        // Act
        List<CouponResponse.Info> result = couponService.getAll();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo(COUPON_CODE);
        verify(couponRepository).findAll();
        verify(couponMapper).toInfoListFromCoupons(coupons);
    }

    @Test
    @DisplayName("getAll: 쿠폰이 하나도 없을 때 빈 리스트를 반환한다")
    void getAll_returnsEmptyList_whenNoCoupons() {
        // Arrange
        when(couponRepository.findAll()).thenReturn(Collections.emptyList());
        when(couponMapper.toInfoListFromCoupons(Collections.emptyList())).thenReturn(Collections.emptyList());

        // Act
        List<CouponResponse.Info> result = couponService.getAll();

        // Assert
        assertThat(result).isEmpty();
    }

    // ====================================================================
    //  getMyCoupons — Redis 캐시 히트
    // ====================================================================

    @Test
    @DisplayName("getMyCoupons: Redis에 쿠폰 ID 목록이 있으면(캐시 히트) Redis 데이터로 응답한다")
    void getMyCoupons_cacheHit_returnsResponseFromRedis() {
        // Arrange
        Set<String> cachedIds = Set.of(COUPON_ID);
        List<Coupon> coupons = List.of(testCoupon);
        List<CouponResponse.Info> expected = List.of(
                CouponResponse.Info.builder().couponId(COUPON_ID).code(COUPON_CODE).build()
        );

        when(couponRedisKeyManager.getUserCouponSetKey(USER_ID)).thenReturn(USER_COUPON_KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(USER_COUPON_KEY)).thenReturn(cachedIds);
        when(couponRepository.findByPublicIdIn(cachedIds)).thenReturn(coupons);
        when(couponMapper.toInfoListFromCoupons(coupons)).thenReturn(expected);

        // Act
        List<CouponResponse.Info> result = couponService.getMyCoupons(USER_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponId()).isEqualTo(COUPON_ID);

        // DB fallback 경로가 호출되지 않았음을 확인
        verify(couponIssueRepository, never()).findByUserId(anyString());
    }

    // ====================================================================
    //  getMyCoupons — Redis 캐시 미스, DB fallback
    // ====================================================================

    @Test
    @DisplayName("getMyCoupons: Redis에 데이터가 없으면(캐시 미스) DB에서 조회하고 Redis에 캐싱한다")
    void getMyCoupons_cacheMiss_fallbackToDb_andCachesResult() {
        // Arrange
        Coupon couponInIssue = Coupon.builder()
                .id(1L)
                .publicId(COUPON_ID)
                .code(COUPON_CODE)
                .name("테스트 쿠폰")
                .build();

        CouponIssue couponIssue = CouponIssue.builder()
                .id(10L)
                .coupon(couponInIssue)
                .userId(USER_ID)
                .issuedAt(LocalDateTime.now())
                .status(CouponIssueStatus.ISSUED)
                .publicId("issue_pub_001")
                .build();

        List<CouponIssue> issuesFromDb = List.of(couponIssue);
        List<CouponResponse.Info> expected = List.of(
                CouponResponse.Info.builder().couponId(COUPON_ID).code(COUPON_CODE).build()
        );

        when(couponRedisKeyManager.getUserCouponSetKey(USER_ID)).thenReturn(USER_COUPON_KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(USER_COUPON_KEY)).thenReturn(Collections.emptySet());
        when(couponIssueRepository.findByUserId(USER_ID)).thenReturn(issuesFromDb);
        when(setOperations.add(eq(USER_COUPON_KEY), any(String[].class))).thenReturn(1L);
        when(couponMapper.toInfoListFromCouponIssues(issuesFromDb)).thenReturn(expected);

        // Act
        List<CouponResponse.Info> result = couponService.getMyCoupons(USER_ID);

        // Assert
        assertThat(result).hasSize(1);

        // Redis에 캐싱이 이루어졌음을 확인
        verify(setOperations).add(eq(USER_COUPON_KEY), any(String[].class));
        verify(couponRepository, never()).findByPublicIdIn(any());
    }

    @Test
    @DisplayName("getMyCoupons: Redis 캐시 미스이고 DB에도 데이터가 없으면 빈 리스트를 반환한다")
    void getMyCoupons_cacheMiss_dbEmpty_returnsEmptyList() {
        // Arrange
        when(couponRedisKeyManager.getUserCouponSetKey(USER_ID)).thenReturn(USER_COUPON_KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(USER_COUPON_KEY)).thenReturn(null);
        when(couponIssueRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
        when(couponMapper.toInfoListFromCouponIssues(Collections.emptyList())).thenReturn(Collections.emptyList());

        // Act
        List<CouponResponse.Info> result = couponService.getMyCoupons(USER_ID);

        // Assert
        assertThat(result).isEmpty();

        // 빈 리스트일 때 Redis Set add가 호출되지 않아야 함
        verify(setOperations, never()).add(anyString(), any(String[].class));
    }

    @Test
    @DisplayName("getMyCoupons: Redis members 반환값이 null이면 DB fallback 경로를 탄다")
    void getMyCoupons_redisReturnsNull_fallbackToDb() {
        // Arrange
        when(couponRedisKeyManager.getUserCouponSetKey(USER_ID)).thenReturn(USER_COUPON_KEY);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(USER_COUPON_KEY)).thenReturn(null);
        when(couponIssueRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
        when(couponMapper.toInfoListFromCouponIssues(Collections.emptyList())).thenReturn(Collections.emptyList());

        // Act
        List<CouponResponse.Info> result = couponService.getMyCoupons(USER_ID);

        // Assert
        verify(couponIssueRepository).findByUserId(USER_ID);
        assertThat(result).isEmpty();
    }
}
