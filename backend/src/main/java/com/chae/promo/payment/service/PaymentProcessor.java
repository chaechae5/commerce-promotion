package com.chae.promo.payment.service;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.entity.UserType;
import com.chae.promo.order.event.OrderPlacedEvent;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.outbox.service.OutboxService;
import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentApprovalContext;
import com.chae.promo.payment.entity.Payment;
import com.chae.promo.payment.entity.PaymentAttempt;
import com.chae.promo.payment.repository.PaymentAttemptRepository;
import com.chae.promo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessor {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final PaymentAttemptRepository paymentAttemptRepository;


    @Transactional
    public ApproveResult processApprovalResult(String orderId,
                                               String paymentId,
                                               PaymentApprovalContext context,
                                               String userId,
                                               UserType userType) {

        Order order = orderRepository.findByPublicId(orderId)
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        //멱등 처리
        if (order.getStatus() == OrderStatus.PAID) {
            return new ApproveResult(paymentId, order.getTotalPrice());
        }

        // 결제 가능한 상태인지 검증
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {

            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> {
                    log.warn("결제 정보가 존재하지 않습니다. 주문 ID: {}", order.getPublicId());

                    return new CommonCustomException(CommonErrorCode.PAYMENT_NOT_FOUND);
                });

        if (context.success()) {
            // 도메인 상태 변경 (성공)
            payment.markPaid(paymentId, context.paymentMethod(), context.paidAt());
            order.markPaid();

            if (context.integrityFailed()) {
                log.error("결제 성공했지만 무결성 검증 실패! 주문 ID: {}", order.getPublicId());
                // TODO: 알림 처리 필요
            }

            // 결제 성공 시도 기록
            savePaymentSuccessAttempt(payment, context);

            // 결제 승인 후 처리 - outbox 저장
            enqueueOrderPlacedOutbox(order, userId, userType);

            return new ApproveResult(paymentId, order.getTotalPrice());
        } else {
            //도메인 상태 변경 x - payment : pending / order : 결제 대기 상태 유지
            log.warn("결제 승인 실패: {}, 주문 ID: {}", context.failureMessage(), order.getPublicId());

            // 실패한 결제 시도 기록
            savePaymentFailedAttempt(payment, context);

            throw new CommonCustomException(CommonErrorCode.PAYMENT_APPROVAL_FAILED);
        }
    }

    private void savePaymentSuccessAttempt(Payment payment, PaymentApprovalContext context) {
        PaymentAttempt successAttempt = PaymentAttempt.success(
                payment,
                payment.getAmount(),
                context.paymentMethod(),
                context.paidAt()
        );

        paymentAttemptRepository.save(successAttempt);

    }

    private void savePaymentFailedAttempt(Payment payment, PaymentApprovalContext context) {

        PaymentAttempt failedAttempt = PaymentAttempt.failure(
                payment,
                payment.getAmount(),
                payment.getPaymentMethod(),
                context.failureMessage(),
                LocalDateTime.now() //현재 시각으로 시도 시간 기록
        );

        paymentAttemptRepository.save(failedAttempt);
    }

    // 결제 완료 후 처리 - outbox 저장
    private void enqueueOrderPlacedOutbox(Order order, String userId, UserType userType) {
        List<OrderPlacedEvent.Item> eventItems = order.getOrderItems().stream()
                .map(item -> OrderPlacedEvent.Item.builder()
                        .productCode(item.getProduct().getCode())
                        .decreasedStock(item.getQuantity())
                        .build())
                .toList();

        String eventId = UuidUtil.generate();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .eventId(eventId)
                .userId(userId)
                .userType(userType)
                .orderPublicId(order.getPublicId())
                .items(eventItems)
                .build();

        outboxService.saveEvent(
                eventId,
                TopicNames.ORDER_PLACED,
                order.getPublicId(),
                event
        );
    }

}