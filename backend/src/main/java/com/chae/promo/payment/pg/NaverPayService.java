package com.chae.promo.payment.pg;

import com.chae.promo.order.entity.Order;
import com.chae.promo.payment.dto.NaverPayApproveResponse;
import com.chae.promo.payment.dto.PaymentApprovalContext;
import com.chae.promo.payment.dto.PaymentApprove;
import com.chae.promo.payment.entity.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

//응답해석, 검증
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverPayService {
    private final NaverPayClient client;

    public PaymentApprovalContext approve(PaymentApprove request, Order order) {
        NaverPayApproveResponse response = client.approve(request.getPaymentId());
        var detail = response.body().detail();

        // 승인 실패
        if (!"SUCCESS".equalsIgnoreCase(response.code())) {
            log.warn("결제 승인 실패: {}", detail.admissionState());

            return PaymentApprovalContext.failure(response.message());
        }

        boolean integrityFailed = false;

        // 금액 검증
        BigDecimal paidAmount = BigDecimal.valueOf(detail.totalPayAmount());
        if (paidAmount.compareTo(order.getTotalPrice()) != 0) {
            //로그만 남기기 (실제 서비스에서는 관리자 알림 등 추가 조치 필요)
            integrityFailed = true;

            log.warn("결제 금액 불일치. 주문 금액 = {}, 결제 금액 = {}", order.getTotalPrice(), paidAmount);

//            throw new CommonCustomException(CommonErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 주문 ID 검증
        if (!request.getOrderId().equals(detail.merchantPayKey())) {
            //로그만 남기기 (실제 서비스에서는 관리자 알림 등 추가 조치 필요)
            integrityFailed = true;
            log.warn("결제 주문 ID 불일치. 요청 주문 ID = {}, 결제 주문 ID = {}",
                    request.getOrderId(), detail.merchantPayKey());
//            throw new CommonCustomException(CommonErrorCode.PAYMENT_ORDER_MISMATCH);
        }

        PaymentMethod method = PaymentMethod.fromValue(detail.primaryPayMeans());
        LocalDateTime paidAt = parseNaverYmdt(detail.admissionYmdt());

        return PaymentApprovalContext.success(method, paidAt, integrityFailed);
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
}