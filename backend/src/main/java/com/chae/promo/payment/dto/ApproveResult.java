package com.chae.promo.payment.dto;

import java.math.BigDecimal;

public record ApproveResult(String paymentId, BigDecimal approvedAmount) {}

