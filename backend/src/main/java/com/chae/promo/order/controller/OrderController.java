package com.chae.promo.order.controller;

import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.service.OrderService;
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
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;


    @Deprecated
    @PostMapping("/purchase")
    public ResponseEntity<OrderResponse.Purchase> purchase(
            @RequestBody @Valid OrderRequest.Purchase request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        return ResponseEntity.ok(orderService.placeOrder(request, userDetails.getUsername()));
    }

    //주문 요청
    @PostMapping
    public ResponseEntity<OrderResponse.OrderSummary> createOrder(
            @RequestBody @Valid OrderRequest.Create request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        return ResponseEntity.ok(orderService.createOrder(request, userDetails.getUsername()));
    }
}
