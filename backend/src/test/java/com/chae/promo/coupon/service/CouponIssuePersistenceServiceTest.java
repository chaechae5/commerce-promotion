package com.chae.promo.coupon.service;

// Tested: CouponIssuePersistenceService
//   - saveCouponIssue(): 정상 저장 — CouponIssue 엔티티가 올바른 필드로 저장되고 반환된다
//   - saveCouponIssue(): 저장된 CouponIssue에 올바른 상태값(ISSUED)이 설정된다
//   - saveCouponIssue(): repository.save()가 정확히 한 번 호출된다
//   - saveCouponIssue(): repository.save() 예외 발생 시 예외가 전파된다

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import com.chae.promo.coupon.entity.CouponIssueStatus;
import com.chae.promo.coupon.repository.CouponIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssuePersistenceService 테스트")
class CouponIssuePersistenceServiceTest {

    @InjectMocks
    private CouponIssuePersistenceService couponIssuePersistenceService;

    @Mock
    private CouponIssueRepository couponIssueRepository;

    // -------- 공통 픽스처 --------
    private static final String USER_ID         = "user_001";
    private static final String COUPON_PUBLIC_ID = "coupon_pub_001";
    private static final String COUPON_CODE     = "PROMO2024";
    private static final String ISSUE_PUBLIC_ID = "issue_pub_001";

    private Coupon testCoupon;
    private LocalDateTime expireAt;

    @BeforeEach
    void setUp() {
        testCoupon = Coupon.builder()
                .id(1L)
                .publicId(COUPON_PUBLIC_ID)
                .code(COUPON_CODE)
                .name("테스트 쿠폰")
                .totalQuantity(100)
                .build();

        expireAt = LocalDateTime.now().plusDays(7);
    }

    // ====================================================================
    //  saveCouponIssue — 정상 저장
    // ====================================================================

    @Test
    @DisplayName("saveCouponIssue: 정상 저장 시 repository.save()의 반환값을 그대로 반환한다")
    void saveCouponIssue_success_returnsPersistedCouponIssue() {
        // Arrange
        CouponIssue expectedSaved = CouponIssue.builder()
                .id(10L)
                .coupon(testCoupon)
                .userId(USER_ID)
                .issuedAt(LocalDateTime.now())
                .expireAt(expireAt)
                .status(CouponIssueStatus.ISSUED)
                .publicId(ISSUE_PUBLIC_ID)
                .build();

        when(couponIssueRepository.save(any(CouponIssue.class))).thenReturn(expectedSaved);

        // Act
        CouponIssue result = couponIssuePersistenceService.saveCouponIssue(
                testCoupon, USER_ID, expireAt, ISSUE_PUBLIC_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(result.getExpireAt()).isEqualTo(expireAt);
        assertThat(result.getPublicId()).isEqualTo(ISSUE_PUBLIC_ID);
    }

    @Test
    @DisplayName("saveCouponIssue: repository.save()에 올바른 필드를 가진 CouponIssue 객체가 전달된다")
    void saveCouponIssue_success_passesCorrectEntityToRepository() {
        // Arrange
        CouponIssue stubSaved = CouponIssue.builder()
                .id(10L)
                .coupon(testCoupon)
                .userId(USER_ID)
                .issuedAt(LocalDateTime.now())
                .expireAt(expireAt)
                .status(CouponIssueStatus.ISSUED)
                .publicId(ISSUE_PUBLIC_ID)
                .build();

        ArgumentCaptor<CouponIssue> captor = ArgumentCaptor.forClass(CouponIssue.class);
        when(couponIssueRepository.save(captor.capture())).thenReturn(stubSaved);

        // Act
        couponIssuePersistenceService.saveCouponIssue(testCoupon, USER_ID, expireAt, ISSUE_PUBLIC_ID);

        // Assert — save()에 전달된 엔티티 필드 검증
        CouponIssue passed = captor.getValue();
        assertThat(passed.getCoupon()).isEqualTo(testCoupon);
        assertThat(passed.getUserId()).isEqualTo(USER_ID);
        assertThat(passed.getExpireAt()).isEqualTo(expireAt);
        assertThat(passed.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(passed.getPublicId()).isEqualTo(ISSUE_PUBLIC_ID);
        // issuedAt은 LocalDateTime.now()로 설정되므로 null이 아님을 확인
        assertThat(passed.getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("saveCouponIssue: repository.save()가 정확히 한 번 호출된다")
    void saveCouponIssue_success_callsRepositorySaveExactlyOnce() {
        // Arrange
        CouponIssue stubSaved = CouponIssue.builder()
                .id(10L)
                .coupon(testCoupon)
                .userId(USER_ID)
                .status(CouponIssueStatus.ISSUED)
                .publicId(ISSUE_PUBLIC_ID)
                .build();

        when(couponIssueRepository.save(any(CouponIssue.class))).thenReturn(stubSaved);

        // Act
        couponIssuePersistenceService.saveCouponIssue(testCoupon, USER_ID, expireAt, ISSUE_PUBLIC_ID);

        // Assert
        verify(couponIssueRepository, times(1)).save(any(CouponIssue.class));
        verifyNoMoreInteractions(couponIssueRepository);
    }

    @Test
    @DisplayName("saveCouponIssue: 저장된 CouponIssue 상태는 항상 ISSUED이다")
    void saveCouponIssue_success_statusIsAlwaysIssued() {
        // Arrange
        ArgumentCaptor<CouponIssue> captor = ArgumentCaptor.forClass(CouponIssue.class);

        CouponIssue stubSaved = CouponIssue.builder()
                .id(11L)
                .coupon(testCoupon)
                .userId(USER_ID)
                .status(CouponIssueStatus.ISSUED)
                .publicId(ISSUE_PUBLIC_ID)
                .build();

        when(couponIssueRepository.save(captor.capture())).thenReturn(stubSaved);

        // Act
        couponIssuePersistenceService.saveCouponIssue(testCoupon, USER_ID, expireAt, ISSUE_PUBLIC_ID);

        // Assert
        assertThat(captor.getValue().getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
    }

    // ====================================================================
    //  saveCouponIssue — 저장 예외 전파
    // ====================================================================

    @Test
    @DisplayName("saveCouponIssue: repository.save() 예외 발생 시 예외가 그대로 전파된다")
    void saveCouponIssue_fail_repositoryException_propagatesException() {
        // Arrange
        RuntimeException dbException = new RuntimeException("DB connection failed");
        when(couponIssueRepository.save(any(CouponIssue.class))).thenThrow(dbException);

        // Act & Assert
        assertThatThrownBy(() ->
                couponIssuePersistenceService.saveCouponIssue(testCoupon, USER_ID, expireAt, ISSUE_PUBLIC_ID)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection failed");
    }
}
