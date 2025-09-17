package com.chae.promo.payment.service;

import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentApprove;
import com.chae.promo.payment.dto.PaymentPrepareRequest;

public interface PaymentService {
    OrderResponse.OrderSummary prepare(PaymentPrepareRequest request, String userId);
    ApproveResult approve(PaymentApprove request, String userId);
}
