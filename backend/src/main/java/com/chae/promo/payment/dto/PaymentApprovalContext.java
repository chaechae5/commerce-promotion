package com.chae.promo.payment.dto;

import com.chae.promo.payment.entity.PaymentMethod;

import java.time.LocalDateTime;

public record PaymentApprovalContext(PaymentMethod paymentMethod, LocalDateTime paidAt) {}

