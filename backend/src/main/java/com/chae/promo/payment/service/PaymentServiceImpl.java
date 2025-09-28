package com.chae.promo.payment.service;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.entity.UserType;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.payment.dto.*;
import com.chae.promo.payment.entity.Payment;
import com.chae.promo.payment.entity.PaymentStatus;
import com.chae.promo.payment.pg.NaverPayService;
import com.chae.promo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final NaverPayService naverPayService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProcessor paymentProcessor;


    @Transactional
    @Override
    public OrderResponse.OrderSummary prepare(PaymentPrepareRequest request, String userId) {
        Order order = orderRepository.findByPublicId(request.orderId())
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        //결제 가능 상태인지, 주문자와 결제자가 일치하는지 검증
        validateOrderForPayment(order, userId);

        //결제 금액이 주문 금액과 일치하는지 검증
        validatePaymentAmount(order, request.paymentMethod());

        //주문 상태를 결제 대기 상태로 변경
        order.markPendingPayment();

        //결제 정보 생성 및 저장 - 결제 승인 전(PENDING)
        createAndSavePayment(order, request);

        return OrderResponse.OrderSummary.builder()
                .publicId(order.getPublicId())
                .itemCount(order.getOrderItems().size())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus().name())
                .build();

    }

    private void validateOrderForPayment(Order order, String userId) {
        //결제 가능한 상태인지 검증
        if (order.getStatus() != OrderStatus.CREATED) {
            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        validateOrdererForPayment(order, userId);
    }

    //주문자와 결제자가 일치하는지 검증
    private UserType validateOrdererForPayment(Order order, String userId) {
        if (order.getCustomerId() == null) {
            if (!order.getOrdererName().contains(userId)) {
                log.warn("비회원 주문자와 결제자가 일치하지 않습니다. 주문자: {}, 결제자: {}", order.getOrdererName(), userId);
                throw new CommonCustomException(CommonErrorCode.NOT_ALLOWED);
            }
            return UserType.GUEST;
        } else {
            if (!String.valueOf(order.getCustomerId()).equals(userId)) {
                log.warn("주문자와 결제자가 일치하지 않습니다. 주문자: {}, 결제자: {}", order.getCustomerId(), userId);
                throw new CommonCustomException(CommonErrorCode.NOT_ALLOWED);
            }
            return UserType.MEMBER;
        }
    }

    //결제 금액이 주문 금액과 일치하는지 검증
    private void validatePaymentAmount(Order order, PaymentMethodRequest request) {

        if (order.getTotalPrice().compareTo(request.amount()) != 0) {
            log.warn("결제 금액 불일치. 주문 금액: {}, 요청 금액: {}", order.getTotalPrice(), request.amount());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

    }

    private void createAndSavePayment(Order order, PaymentPrepareRequest request) {

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(request.paymentMethod().method())
                .amount(order.getTotalPrice())
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);
    }


    @Override
    public ApproveResult approve(PaymentApprove request, String userId) {
        String paymentId = request.getPaymentId();

        Order order = orderRepository.findByPublicId(request.getOrderId())
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));


        //주문자 검증
        UserType userType = validateOrdererForPayment(order, userId);

        //멱등 처리
        if (order.getStatus() == OrderStatus.PAID) {
            return new ApproveResult(paymentId, order.getTotalPrice());
        }

        // 결제 가능한 상태인지 검증
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {

            log.warn("결제 불가능한 상태: {}, 주문 ID: {}", order.getStatus(), order.getPublicId());
            throw new CommonCustomException(CommonErrorCode.PAYMENT_NOT_ALLOWED_STATE);
        }

        //결제 정보 검증
        paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> {
                    log.warn("결제 정보가 존재하지 않습니다. 주문 ID: {}", order.getPublicId());

                    return new CommonCustomException(CommonErrorCode.PAYMENT_NOT_FOUND);
                });

        try {
            PaymentApprovalContext context = naverPayService.approve(request, order);

            return paymentProcessor.processApprovalResult(order.getPublicId(), paymentId, context, userId, userType);

        } catch (Exception e) {
            log.error("결제 승인 처리 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }

    }

}