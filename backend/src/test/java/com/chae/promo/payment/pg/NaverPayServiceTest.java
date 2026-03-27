// Tested functions:
//   NaverPayService.approve() — PG 승인 성공(정상), PG 코드 실패(failure context 반환),
//                               금액 불일치(integrityFailed=true), 주문ID 불일치(integrityFailed=true),
//                               금액+주문ID 동시 불일치, admissionYmdt 포맷 불량 예외
package com.chae.promo.payment.pg;

import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.payment.dto.NaverPayApproveResponse;
import com.chae.promo.payment.dto.PaymentApprovalContext;
import com.chae.promo.payment.dto.PaymentApprove;
import com.chae.promo.payment.entity.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("NaverPayService 테스트")
@ExtendWith(MockitoExtension.class)
class NaverPayServiceTest {

    @Mock
    private NaverPayClient client;

    @InjectMocks
    private NaverPayService naverPayService;

    // -----------------------------------------------------------------------
    // 공통 픽스처 헬퍼
    // -----------------------------------------------------------------------

    private Order buildOrder(String publicId, BigDecimal totalPrice) {
        return Order.builder()
                .id(1L)
                .publicId(publicId)
                .customerId(42L)
                .ordererName("홍길동")
                .totalPrice(totalPrice)
                .status(OrderStatus.PENDING_PAYMENT)
                .orderItems(new ArrayList<>())
                .productName("테스트 상품")
                .build();
    }

    private PaymentApprove buildRequest(String orderId, String paymentId) {
        return PaymentApprove.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .build();
    }

    /**
     * NaverPayApproveResponse.Detail 빌더 역할의 헬퍼.
     * 기본값이 채워진 Detail 레코드를 생성하고, 필요한 값만 재정의하는 방식으로 사용한다.
     */
    private NaverPayApproveResponse buildSuccessResponse(
            String code,
            String message,
            long totalPayAmount,
            String merchantPayKey,
            String primaryPayMeans,
            String admissionYmdt
    ) {
        NaverPayApproveResponse.Detail detail = new NaverPayApproveResponse.Detail(
                "pay-001",         // paymentId
                "hist-001",        // payHistId
                "테스트가맹점",        // merchantName
                "merchant-001",    // merchantId
                merchantPayKey,    // merchantPayKey (주문 ID 검증에 사용)
                "user-001",        // merchantUserKey
                "01",              // admissionTypeCode
                admissionYmdt,     // admissionYmdt (yyyyMMddHHmmss)
                "20241201120000",  // tradeConfirmYmdt
                "SUCCESS",         // admissionState
                totalPayAmount,    // totalPayAmount
                totalPayAmount,    // applyPayAmount
                totalPayAmount,    // primaryPayAmount
                0L,                // npointPayAmount
                0L,                // giftCardAmount
                0L,                // discountPayAmount
                totalPayAmount,    // taxScopeAmount
                0L,                // taxExScopeAmount
                0L,                // environmentDepositAmount
                primaryPayMeans,   // primaryPayMeans
                "VISA",            // cardCorpCode
                "1234-****-****-5678", // cardNo
                "auth-123",        // cardAuthNo
                0,                 // cardInstCount
                false,             // usedCardPoint
                null,              // bankCorpCode
                null,              // bankAccountNo
                "테스트 상품",         // productName
                false,             // settleExpected
                0L,                // settleExpectAmount
                0L,                // payCommissionAmount
                null,              // merchantExtraParameter
                null,              // merchantPayTransactionKey
                "user-identifier", // userIdentifier
                false,             // extraDeduction
                null,              // useCfmYmdt
                null               // subMerchantInfo
        );
        NaverPayApproveResponse.Body body = new NaverPayApproveResponse.Body("pay-001", detail);
        return new NaverPayApproveResponse(code, message, body);
    }

    // -----------------------------------------------------------------------
    // approve() 테스트
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("approve() - 네이버페이 결제 승인 해석")
    class ApproveTests {

        @Test
        @DisplayName("PG 승인 성공: success=true, integrityFailed=false인 PaymentApprovalContext를 반환한다")
        void approve_pgSuccess_returnsSuccessContext() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "Success",           // code (대소문자 무관 - equalsIgnoreCase 처리)
                    "OK",
                    10000L,              // totalPayAmount = 10000 (금액 일치)
                    "order-public-001",  // merchantPayKey = orderId (주문 ID 일치)
                    "CARD",
                    "20241201120000"     // 유효한 yyyyMMddHHmmss 포맷
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act
            PaymentApprovalContext context = naverPayService.approve(request, order);

            // Assert
            assertThat(context.success()).isTrue();
            assertThat(context.integrityFailed()).isFalse();
            assertThat(context.paymentMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(context.paidAt()).isNotNull();
            assertThat(context.failureMessage()).isNull();
        }

        @Test
        @DisplayName("PG 코드가 SUCCESS가 아니면 success=false인 failure context를 반환한다")
        void approve_pgCodeNotSuccess_returnsFailureContext() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse.Detail detail = new NaverPayApproveResponse.Detail(
                    "pay-001", "hist-001", "테스트가맹점", "merchant-001",
                    "order-public-001", "user-001", "01",
                    "20241201120000", "20241201120000",
                    "FAIL", // admissionState
                    10000L, 10000L, 10000L, 0L, 0L, 0L, 10000L, 0L, 0L,
                    "CARD", "VISA", "****", "auth", 0, false,
                    null, null, "테스트 상품", false, 0L, 0L, null, null,
                    "uid", false, null, null
            );
            NaverPayApproveResponse.Body body = new NaverPayApproveResponse.Body("pay-001", detail);
            NaverPayApproveResponse response = new NaverPayApproveResponse("FAIL", "결제 거절", body);

            when(client.approve("pay-001")).thenReturn(response);

            // Act
            PaymentApprovalContext context = naverPayService.approve(request, order);

            // Assert
            assertThat(context.success()).isFalse();
            assertThat(context.failureMessage()).isEqualTo("결제 거절");
            assertThat(context.paymentMethod()).isNull();
            assertThat(context.paidAt()).isNull();
        }

        @Test
        @DisplayName("결제 금액이 주문 금액과 다르면 integrityFailed=true인 success context를 반환한다")
        void approve_amountMismatch_returnsSuccessContextWithIntegrityFailed() {
            // Arrange
            BigDecimal orderPrice = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", orderPrice);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    9000L,               // 금액 불일치: 9000 != 10000
                    "order-public-001",  // 주문 ID 일치
                    "CARD",
                    "20241201120000"
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act
            PaymentApprovalContext context = naverPayService.approve(request, order);

            // Assert
            assertThat(context.success()).isTrue();
            assertThat(context.integrityFailed()).isTrue(); // 무결성 검증 실패 플래그
        }

        @Test
        @DisplayName("주문 ID(merchantPayKey)가 요청 orderId와 다르면 integrityFailed=true인 success context를 반환한다")
        void approve_orderIdMismatch_returnsSuccessContextWithIntegrityFailed() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    10000L,              // 금액 일치
                    "order-DIFFERENT",   // 주문 ID 불일치
                    "CARD",
                    "20241201120000"
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act
            PaymentApprovalContext context = naverPayService.approve(request, order);

            // Assert
            assertThat(context.success()).isTrue();
            assertThat(context.integrityFailed()).isTrue();
        }

        @Test
        @DisplayName("금액 불일치 + 주문ID 불일치 모두 해당할 경우 integrityFailed=true인 success context를 반환한다")
        void approve_amountAndOrderIdBothMismatch_returnsSuccessContextWithIntegrityFailed() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    1L,                  // 금액 불일치
                    "order-WRONG",       // 주문 ID 불일치
                    "CARD",
                    "20241201120000"
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act
            PaymentApprovalContext context = naverPayService.approve(request, order);

            // Assert
            assertThat(context.success()).isTrue();
            assertThat(context.integrityFailed()).isTrue();
        }

        @Test
        @DisplayName("admissionYmdt가 null이면 IllegalArgumentException이 발생한다")
        void approve_nullAdmissionYmdt_throwsIllegalArgumentException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    10000L,
                    "order-public-001",
                    "CARD",
                    null               // null ymdt
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act & Assert
            assertThatThrownBy(() -> naverPayService.approve(request, order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid ymdt");
        }

        @Test
        @DisplayName("admissionYmdt가 14자리 숫자가 아니면 IllegalArgumentException이 발생한다")
        void approve_invalidAdmissionYmdtFormat_throwsIllegalArgumentException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    10000L,
                    "order-public-001",
                    "CARD",
                    "2024-12-01 12:00" // 잘못된 포맷 (14자리 숫자 아님)
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act & Assert
            assertThatThrownBy(() -> naverPayService.approve(request, order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid ymdt");
        }

        @Test
        @DisplayName("admissionYmdt가 숫자가 아닌 문자를 포함하면 IllegalArgumentException이 발생한다")
        void approve_nonDigitAdmissionYmdt_throwsIllegalArgumentException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    10000L,
                    "order-public-001",
                    "CARD",
                    "2024120112000A"  // 마지막 문자가 숫자가 아님
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act & Assert
            assertThatThrownBy(() -> naverPayService.approve(request, order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid ymdt");
        }

        @Test
        @DisplayName("primaryPayMeans가 인식 불가한 값이면 VALIDATION_FAILED CommonCustomException이 발생한다")
        void approve_unknownPrimaryPayMeans_throwsValidationFailedException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = buildOrder("order-public-001", price);
            PaymentApprove request = buildRequest("order-public-001", "pay-001");

            NaverPayApproveResponse response = buildSuccessResponse(
                    "SUCCESS",
                    "OK",
                    10000L,
                    "order-public-001",
                    "CRYPTO",             // PaymentMethod enum에 없는 값
                    "20241201120000"
            );
            when(client.approve("pay-001")).thenReturn(response);

            // Act & Assert
            assertThatThrownBy(() -> naverPayService.approve(request, order))
                    .isInstanceOf(com.chae.promo.exception.CommonCustomException.class)
                    .extracting(e -> ((com.chae.promo.exception.CommonCustomException) e).getErrorCode())
                    .isEqualTo(com.chae.promo.exception.CommonErrorCode.VALIDATION_FAILED);
        }
    }
}
