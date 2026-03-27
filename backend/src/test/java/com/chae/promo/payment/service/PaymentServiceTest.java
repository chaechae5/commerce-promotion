// Tested functions:
//   PaymentServiceImpl.prepare()  — 결제 준비 레코드 생성, 주문 없음 예외, 금액 불일치 예외, 권한 없음(회원/비회원) 예외, 결제 불가 상태 예외
//   PaymentServiceImpl.approve()  — 성공(결제 승인), 멱등(이미 PAID), 결제 불가 상태 예외, 결제 정보 없음 예외, PG 실패 예외 전파
package com.chae.promo.payment.service;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentApprovalContext;
import com.chae.promo.payment.dto.PaymentApprove;
import com.chae.promo.payment.dto.PaymentMethodRequest;
import com.chae.promo.payment.dto.PaymentPrepareRequest;
import com.chae.promo.payment.entity.Payment;
import com.chae.promo.payment.entity.PaymentMethod;
import com.chae.promo.payment.entity.PaymentStatus;
import com.chae.promo.payment.pg.NaverPayService;
import com.chae.promo.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PaymentServiceImpl 테스트")
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private NaverPayService naverPayService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProcessor paymentProcessor;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // -----------------------------------------------------------------------
    // 공통 픽스처 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 회원 주문 픽스처: customerId = 42, status = CREATED, totalPrice = 10000
     */
    private Order memberOrderCreated(BigDecimal totalPrice) {
        return Order.builder()
                .id(1L)
                .publicId("order-public-001")
                .customerId(42L)
                .ordererName("홍길동")
                .totalPrice(totalPrice)
                .status(OrderStatus.CREATED)
                .orderItems(new ArrayList<>())
                .productName("테스트 상품")
                .build();
    }

    /**
     * 비회원 주문 픽스처: customerId = null, ordererName = "guest:guest-user-001", status = CREATED
     */
    private Order guestOrderCreated(BigDecimal totalPrice) {
        return Order.builder()
                .id(2L)
                .publicId("order-public-002")
                .customerId(null)
                .ordererName("guest:guest-user-001")
                .totalPrice(totalPrice)
                .status(OrderStatus.CREATED)
                .orderItems(new ArrayList<>())
                .productName("테스트 상품")
                .build();
    }

    private PaymentPrepareRequest prepareRequest(String orderId, BigDecimal amount) {
        PaymentMethodRequest method = new PaymentMethodRequest(PaymentMethod.CARD, amount);
        return new PaymentPrepareRequest(orderId, method);
    }

    private Payment pendingPayment(Order order) {
        return Payment.builder()
                .order(order)
                .paymentMethod(PaymentMethod.CARD)
                .amount(order.getTotalPrice())
                .status(PaymentStatus.PENDING)
                .build();
    }

    // -----------------------------------------------------------------------
    // prepare() 테스트
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("prepare() - 결제 준비")
    class PrepareTests {

        @Test
        @DisplayName("회원 주문: 정상 결제 준비 시 PENDING_PAYMENT 상태와 OrderSummary를 반환한다")
        void prepare_memberOrder_success() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = memberOrderCreated(price);
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentPrepareRequest request = prepareRequest("order-public-001", price);

            // Act
            OrderResponse.OrderSummary result = paymentService.prepare(request, "42");

            // Assert
            assertThat(result.getPublicId()).isEqualTo("order-public-001");
            assertThat(result.getTotalPrice()).isEqualByComparingTo(price);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

            // Payment 엔티티가 PENDING 상태로 저장되었는지 검증
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(price);
        }

        @Test
        @DisplayName("비회원 주문: ordererName에 userId가 포함되면 정상 준비된다")
        void prepare_guestOrder_success() {
            // Arrange
            BigDecimal price = new BigDecimal("5000");
            Order order = guestOrderCreated(price);
            when(orderRepository.findByPublicId("order-public-002")).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentPrepareRequest request = prepareRequest("order-public-002", price);

            // Act
            OrderResponse.OrderSummary result = paymentService.prepare(request, "guest-user-001");

            // Assert
            assertThat(result.getPublicId()).isEqualTo("order-public-002");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("주문을 찾을 수 없으면 ORDER_NOT_FOUND 예외가 발생한다")
        void prepare_orderNotFound_throwsOrderNotFoundException() {
            // Arrange
            when(orderRepository.findByPublicId("unknown-order")).thenReturn(Optional.empty());
            PaymentPrepareRequest request = prepareRequest("unknown-order", new BigDecimal("10000"));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.prepare(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.ORDER_NOT_FOUND);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("결제 금액이 주문 금액과 다르면 PAYMENT_AMOUNT_MISMATCH 예외가 발생한다")
        void prepare_amountMismatch_throwsPaymentAmountMismatchException() {
            // Arrange
            BigDecimal orderPrice = new BigDecimal("10000");
            BigDecimal requestPrice = new BigDecimal("9999");
            Order order = memberOrderCreated(orderPrice);
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));

            PaymentPrepareRequest request = prepareRequest("order-public-001", requestPrice);

            // Act & Assert
            assertThatThrownBy(() -> paymentService.prepare(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.PAYMENT_AMOUNT_MISMATCH);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("회원 주문: 다른 회원이 결제 시도하면 NOT_ALLOWED 예외가 발생한다")
        void prepare_memberOrder_wrongUser_throwsNotAllowedException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = memberOrderCreated(price);
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));

            PaymentPrepareRequest request = prepareRequest("order-public-001", price);

            // Act & Assert
            assertThatThrownBy(() -> paymentService.prepare(request, "99")) // 주문자 ID는 42
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.NOT_ALLOWED);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("비회원 주문: ordererName에 userId가 없으면 NOT_ALLOWED 예외가 발생한다")
        void prepare_guestOrder_wrongUser_throwsNotAllowedException() {
            // Arrange
            BigDecimal price = new BigDecimal("5000");
            Order order = guestOrderCreated(price); // ordererName = "guest:guest-user-001"
            when(orderRepository.findByPublicId("order-public-002")).thenReturn(Optional.of(order));

            PaymentPrepareRequest request = prepareRequest("order-public-002", price);

            // Act & Assert
            assertThatThrownBy(() -> paymentService.prepare(request, "stranger-user"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.NOT_ALLOWED);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("주문 상태가 CREATED가 아니면 PAYMENT_NOT_ALLOWED_STATE 예외가 발생한다")
        void prepare_orderNotInCreatedStatus_throwsPaymentNotAllowedStateException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = Order.builder()
                    .id(1L)
                    .publicId("order-public-001")
                    .customerId(42L)
                    .ordererName("홍길동")
                    .totalPrice(price)
                    .status(OrderStatus.PENDING_PAYMENT) // CREATED가 아님
                    .orderItems(new ArrayList<>())
                    .productName("테스트 상품")
                    .build();
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));

            PaymentPrepareRequest request = prepareRequest("order-public-001", price);

            // Act & Assert
            assertThatThrownBy(() -> paymentService.prepare(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);

            verify(paymentRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // approve() 테스트
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("approve() - 결제 승인")
    class ApproveTests {

        private Order pendingPaymentOrder(BigDecimal totalPrice) {
            return Order.builder()
                    .id(1L)
                    .publicId("order-public-001")
                    .customerId(42L)
                    .ordererName("홍길동")
                    .totalPrice(totalPrice)
                    .status(OrderStatus.PENDING_PAYMENT)
                    .orderItems(new ArrayList<>())
                    .productName("테스트 상품")
                    .build();
        }

        @Test
        @DisplayName("정상 승인: PG 승인 성공 시 ApproveResult를 반환한다")
        void approve_success() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = pendingPaymentOrder(price);
            Payment payment = pendingPayment(order);

            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            PaymentApprovalContext successContext = PaymentApprovalContext.success(
                    PaymentMethod.CARD, LocalDateTime.now(), false);
            when(naverPayService.approve(any(), any())).thenReturn(successContext);

            ApproveResult expectedResult = new ApproveResult("pay-001", price);
            when(paymentProcessor.processApprovalResult(any(), any(), any(), any(), any()))
                    .thenReturn(expectedResult);

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act
            ApproveResult result = paymentService.approve(request, "42");

            // Assert
            assertThat(result.paymentId()).isEqualTo("pay-001");
            assertThat(result.approvedAmount()).isEqualByComparingTo(price);
            verify(naverPayService).approve(request, order);
            verify(paymentProcessor).processApprovalResult(
                    eq("order-public-001"), eq("pay-001"), eq(successContext), eq("42"), any());
        }

        @Test
        @DisplayName("멱등 처리: 이미 PAID 상태인 주문은 PG 호출 없이 ApproveResult를 반환한다")
        void approve_alreadyPaid_returnsIdempotentResult() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = Order.builder()
                    .id(1L)
                    .publicId("order-public-001")
                    .customerId(42L)
                    .ordererName("홍길동")
                    .totalPrice(price)
                    .status(OrderStatus.PAID) // 이미 결제 완료
                    .orderItems(new ArrayList<>())
                    .productName("테스트 상품")
                    .build();

            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act
            ApproveResult result = paymentService.approve(request, "42");

            // Assert
            assertThat(result.paymentId()).isEqualTo("pay-001");
            assertThat(result.approvedAmount()).isEqualByComparingTo(price);

            // PG 호출이 없어야 한다
            verifyNoInteractions(naverPayService);
            verifyNoInteractions(paymentProcessor);
        }

        @Test
        @DisplayName("주문을 찾을 수 없으면 ORDER_NOT_FOUND 예외가 발생한다")
        void approve_orderNotFound_throwsOrderNotFoundException() {
            // Arrange
            when(orderRepository.findByPublicId("unknown-order")).thenReturn(Optional.empty());

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("unknown-order")
                    .paymentId("pay-001")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> paymentService.approve(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 회원이 승인 시도하면 NOT_ALLOWED 예외가 발생한다")
        void approve_wrongUser_throwsNotAllowedException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = pendingPaymentOrder(price);
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> paymentService.approve(request, "99")) // 주문자는 42
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.NOT_ALLOWED);
        }

        @Test
        @DisplayName("주문 상태가 PENDING_PAYMENT가 아니면 PAYMENT_NOT_ALLOWED_STATE 예외가 발생한다")
        void approve_orderNotInPendingPaymentStatus_throwsPaymentNotAllowedStateException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = Order.builder()
                    .id(1L)
                    .publicId("order-public-001")
                    .customerId(42L)
                    .ordererName("홍길동")
                    .totalPrice(price)
                    .status(OrderStatus.CANCELLED) // 결제 불가 상태
                    .orderItems(new ArrayList<>())
                    .productName("테스트 상품")
                    .build();
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> paymentService.approve(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);

            verifyNoInteractions(naverPayService);
        }

        @Test
        @DisplayName("결제 정보(Payment)가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
        void approve_paymentNotFound_throwsPaymentNotFoundException() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = pendingPaymentOrder(price);
            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> paymentService.approve(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.PAYMENT_NOT_FOUND);

            verifyNoInteractions(naverPayService);
        }

        @Test
        @DisplayName("PG(NaverPayService) 승인 호출이 RuntimeException을 던지면 그대로 전파된다")
        void approve_pgThrowsRuntimeException_exceptionPropagates() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = pendingPaymentOrder(price);
            Payment payment = pendingPayment(order);

            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
            when(naverPayService.approve(any(), any()))
                    .thenThrow(new RuntimeException("네이버페이 서버 오류"));

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> paymentService.approve(request, "42"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("네이버페이 서버 오류");

            verifyNoInteractions(paymentProcessor);
        }

        @Test
        @DisplayName("PaymentProcessor가 PAYMENT_APPROVAL_FAILED를 던지면 그대로 전파된다")
        void approve_processorThrowsApprovalFailed_exceptionPropagates() {
            // Arrange
            BigDecimal price = new BigDecimal("10000");
            Order order = pendingPaymentOrder(price);
            Payment payment = pendingPayment(order);

            when(orderRepository.findByPublicId("order-public-001")).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            PaymentApprovalContext failureContext = PaymentApprovalContext.failure("PG 거절");
            when(naverPayService.approve(any(), any())).thenReturn(failureContext);
            when(paymentProcessor.processApprovalResult(any(), any(), any(), any(), any()))
                    .thenThrow(new CommonCustomException(CommonErrorCode.PAYMENT_APPROVAL_FAILED));

            PaymentApprove request = PaymentApprove.builder()
                    .orderId("order-public-001")
                    .paymentId("pay-001")
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> paymentService.approve(request, "42"))
                    .isInstanceOf(CommonCustomException.class)
                    .extracting(e -> ((CommonCustomException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.PAYMENT_APPROVAL_FAILED);
        }
    }
}
