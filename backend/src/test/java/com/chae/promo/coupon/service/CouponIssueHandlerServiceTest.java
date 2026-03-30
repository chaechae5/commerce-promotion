package com.chae.promo.coupon.service;

// Tested: CouponIssueHandlerService
//   - saveCouponIssueFromEvent(): 정상 저장 (쿠폰 조회 성공 + DB 저장 성공)
//   - saveCouponIssueFromEvent(): 쿠폰 미존재 시 RuntimeException("DB 저장 실패로 인한 재처리 요청") 발생
//   - saveCouponIssueFromEvent(): DB 저장 실패 시 RuntimeException("DB 저장 실패로 인한 재처리 요청") 발생
//   - saveCouponIssueFromEvent(): couponIssuePersistenceService에 올바른 인자가 전달되는지 검증

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.event.CouponIssuedEvent;
import com.chae.promo.coupon.repository.CouponRepository;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssueHandlerService 테스트")
class CouponIssueHandlerServiceTest {

    @InjectMocks
    private CouponIssueHandlerService couponIssueHandlerService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponIssuePersistenceService couponIssuePersistenceService;

    // -------- 공통 픽스처 --------
    private static final String USER_ID         = "user_001";
    private static final String COUPON_PUBLIC_ID = "coupon_pub_001";
    private static final String COUPON_CODE     = "PROMO2024";
    private static final String COUPON_ISSUE_ID = "issue_pub_001";
    private static final String EVENT_ID        = "event_001";

    private Coupon testCoupon;
    private CouponIssuedEvent testEvent;

    @BeforeEach
    void setUp() {
        testCoupon = Coupon.builder()
                .id(1L)
                .publicId(COUPON_PUBLIC_ID)
                .code(COUPON_CODE)
                .name("테스트 쿠폰")
                .totalQuantity(100)
                .build();

        testEvent = CouponIssuedEvent.builder()
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .couponPublicId(COUPON_PUBLIC_ID)
                .couponIssueId(COUPON_ISSUE_ID)
                .issuedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusDays(7))
                .couponCode(COUPON_CODE)
                .build();
    }

    // ====================================================================
    //  saveCouponIssueFromEvent — 정상 저장
    // ====================================================================

    @Test
    @DisplayName("saveCouponIssueFromEvent: 쿠폰 조회 성공 및 DB 저장 성공 시 예외 없이 완료된다")
    void saveCouponIssueFromEvent_success_completesWithoutException() {
        // Arrange
        when(couponRepository.findByPublicId(COUPON_PUBLIC_ID)).thenReturn(Optional.of(testCoupon));

        CouponIssue savedIssue = CouponIssue.builder()
                .id(10L)
                .coupon(testCoupon)
                .userId(USER_ID)
                .issuedAt(LocalDateTime.now())
                .expireAt(testEvent.getExpiredAt())
                .status(CouponIssueStatus.ISSUED)
                .publicId(COUPON_ISSUE_ID)
                .build();

        when(couponIssuePersistenceService.saveCouponIssue(
                eq(testCoupon),
                eq(USER_ID),
                eq(testEvent.getExpiredAt()),
                eq(COUPON_ISSUE_ID)
        )).thenReturn(savedIssue);

        // Act — 예외가 발생하지 않아야 함
        couponIssueHandlerService.saveCouponIssueFromEvent(testEvent);

        // Assert
        verify(couponRepository).findByPublicId(COUPON_PUBLIC_ID);
        verify(couponIssuePersistenceService).saveCouponIssue(
                eq(testCoupon),
                eq(USER_ID),
                eq(testEvent.getExpiredAt()),
                eq(COUPON_ISSUE_ID)
        );
    }

    @Test
    @DisplayName("saveCouponIssueFromEvent: couponIssuePersistenceService에 올바른 인자가 전달된다")
    void saveCouponIssueFromEvent_success_passesCorrectArgumentsToPersistenceService() {
        // Arrange
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(5);
        CouponIssuedEvent event = CouponIssuedEvent.builder()
                .eventId(EVENT_ID)
                .userId("user_999")
                .couponPublicId(COUPON_PUBLIC_ID)
                .couponIssueId("issue_999")
                .issuedAt(LocalDateTime.now())
                .expiredAt(expiredAt)
                .couponCode(COUPON_CODE)
                .build();

        when(couponRepository.findByPublicId(COUPON_PUBLIC_ID)).thenReturn(Optional.of(testCoupon));

        CouponIssue stubIssue = CouponIssue.builder()
                .id(20L)
                .coupon(testCoupon)
                .userId("user_999")
                .status(CouponIssueStatus.ISSUED)
                .build();

        ArgumentCaptor<String> userIdCaptor      = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> expireCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> issueIdCaptor     = ArgumentCaptor.forClass(String.class);

        when(couponIssuePersistenceService.saveCouponIssue(
                any(Coupon.class),
                userIdCaptor.capture(),
                expireCaptor.capture(),
                issueIdCaptor.capture()
        )).thenReturn(stubIssue);

        // Act
        couponIssueHandlerService.saveCouponIssueFromEvent(event);

        // Assert — 전달된 인자 검증
        assertThat(userIdCaptor.getValue()).isEqualTo("user_999");
        assertThat(expireCaptor.getValue()).isEqualTo(expiredAt);
        assertThat(issueIdCaptor.getValue()).isEqualTo("issue_999");
    }

    // ====================================================================
    //  saveCouponIssueFromEvent — 쿠폰 미존재 예외
    // ====================================================================

    @Test
    @DisplayName("saveCouponIssueFromEvent: 쿠폰이 존재하지 않으면 RuntimeException(재처리 요청)이 발생한다")
    void saveCouponIssueFromEvent_fail_couponNotFound_throwsRuntimeException() {
        // Arrange
        when(couponRepository.findByPublicId(COUPON_PUBLIC_ID))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> couponIssueHandlerService.saveCouponIssueFromEvent(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 저장 실패로 인한 재처리 요청");

        // DB 저장은 시도조차 하지 않아야 함
        verify(couponIssuePersistenceService, never()).saveCouponIssue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("saveCouponIssueFromEvent: 쿠폰 미존재 시 원인 예외(CommonCustomException)가 RuntimeException에 연결된다")
    void saveCouponIssueFromEvent_fail_couponNotFound_causeIsCommonCustomException() {
        // Arrange
        when(couponRepository.findByPublicId(COUPON_PUBLIC_ID))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> couponIssueHandlerService.saveCouponIssueFromEvent(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(CommonCustomException.class)
                .satisfies(ex -> {
                    CommonCustomException cause = (CommonCustomException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo(CommonErrorCode.COUPON_NOT_FOUND);
                });
    }

    // ====================================================================
    //  saveCouponIssueFromEvent — DB 저장 실패 예외
    // ====================================================================

    @Test
    @DisplayName("saveCouponIssueFromEvent: DB 저장 중 예외 발생 시 RuntimeException(재처리 요청)이 발생한다")
    void saveCouponIssueFromEvent_fail_dbSaveException_throwsRuntimeException() {
        // Arrange
        when(couponRepository.findByPublicId(COUPON_PUBLIC_ID)).thenReturn(Optional.of(testCoupon));
        when(couponIssuePersistenceService.saveCouponIssue(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection error"));

        // Act & Assert
        assertThatThrownBy(() -> couponIssueHandlerService.saveCouponIssueFromEvent(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 저장 실패로 인한 재처리 요청");
    }

    @Test
    @DisplayName("saveCouponIssueFromEvent: DB 저장 실패 시 원인 예외가 RuntimeException에 연결된다")
    void saveCouponIssueFromEvent_fail_dbSaveException_causeIsOriginalException() {
        // Arrange
        RuntimeException dbException = new RuntimeException("DB connection error");

        when(couponRepository.findByPublicId(COUPON_PUBLIC_ID)).thenReturn(Optional.of(testCoupon));
        when(couponIssuePersistenceService.saveCouponIssue(any(), any(), any(), any()))
                .thenThrow(dbException);

        // Act & Assert
        assertThatThrownBy(() -> couponIssueHandlerService.saveCouponIssueFromEvent(testEvent))
                .isInstanceOf(RuntimeException.class)
                .hasCause(dbException);
    }
}
