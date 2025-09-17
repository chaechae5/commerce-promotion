package com.chae.promo.payment.service;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.entity.UserType;
import com.chae.promo.order.event.OrderPlacedEvent;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.outbox.service.OutboxService;
import com.chae.promo.payment.dto.*;
import com.chae.promo.payment.entity.Payment;
import com.chae.promo.payment.entity.PaymentMethod;
import com.chae.promo.payment.entity.PaymentStatus;
import com.chae.promo.payment.pg.NaverPayClient;
import com.chae.promo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final NaverPayClient naver;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;


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


    @Transactional
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

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> {
                    log.warn("결제 정보가 존재하지 않습니다. 주문 ID: {}", order.getPublicId());

                    return new CommonCustomException(CommonErrorCode.PAYMENT_NOT_FOUND);
                });

        try {
            //구현해두었지만 실제 API 호출은 하지 않음(테스트 편의를 위해)
//            PaymentApprovalContext context = approveNaverPay(request, order);

            //테스트를 위한 하드코딩
            PaymentApprovalContext context = new PaymentApprovalContext(
                    PaymentMethod.CARD,
                    LocalDateTime.now()
            );

            // 도메인 상태 변경
            payment.markPaid(request.getPaymentId(), context.paymentMethod(), context.paidAt());
            order.markPaid();

        enqueueOrderPlacedOutbox(order, userId, userType);

            return new ApproveResult(paymentId, order.getTotalPrice());

        } catch (Exception e) {
            log.error("결제 승인 처리 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }

    }

    private static LocalDateTime parseNaverYmdt(String ymdt) {
        DateTimeFormatter NAVER_YMDT =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        if (ymdt == null || ymdt.length() != 14 || !ymdt.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid ymdt: " + ymdt);
        }
        try {
            return LocalDateTime.parse(ymdt, NAVER_YMDT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Failed to parse ymdt: " + ymdt, e);
        }
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

    private PaymentApprovalContext approveNaverPay(PaymentApprove request, Order order) {
        NaverPayApproveResponse response = naver.approve(request.getPaymentId());

        var detail = response.body().detail();

        //승인 실패
        if (!"SUCCESS".equalsIgnoreCase(detail.admissionState())) {
            log.warn("결제 승인 실패: {}", detail.admissionState());
            throw new CommonCustomException(
                    CommonErrorCode.PAYMENT_APPROVAL_FAILED
            );
        }

        // 금액 검증
        BigDecimal paidAmount = BigDecimal.valueOf(detail.totalPayAmount()); //long -> BigDecimal
        if (paidAmount.compareTo(order.getTotalPrice()) != 0) {
            log.warn("결제 금액 불일치. 주문 금액 = {}, 결제 금액 = {}", order.getTotalPrice(), paidAmount);
            throw new CommonCustomException(CommonErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 주문 ID 검증
        if (!request.getOrderId().equals(detail.merchantPayKey())) {
            throw new CommonCustomException(
                    CommonErrorCode.PAYMENT_ORDER_MISMATCH
            );
        }

        PaymentMethod paymentMethod = PaymentMethod.fromValue(detail.primaryPayMeans());
        LocalDateTime paidAt = parseNaverYmdt(detail.admissionYmdt());


        return new PaymentApprovalContext(paymentMethod, paidAt);
    }
}