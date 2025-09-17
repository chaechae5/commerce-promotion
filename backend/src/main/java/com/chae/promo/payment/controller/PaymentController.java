package com.chae.promo.payment.controller;

import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.payment.dto.ApproveResult;
import com.chae.promo.payment.dto.PaymentPrepareRequest;
import com.chae.promo.payment.dto.PaymentApprove;
import com.chae.promo.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "결제 api")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/approve")
    @Operation(summary = "결제 승인")
    public ResponseEntity<ApproveResult> approvePayment(
            @RequestBody @Valid PaymentApprove request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        ApproveResult result = paymentService.approve(request, userDetails.getUsername());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/prepare")
    @Operation(summary = "결제 준비")
    public ResponseEntity<OrderResponse.OrderSummary> prepare(
            @RequestBody @Valid PaymentPrepareRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        OrderResponse.OrderSummary result = paymentService.prepare(request, userDetails.getUsername());
        return ResponseEntity.ok(result);
    }

}
