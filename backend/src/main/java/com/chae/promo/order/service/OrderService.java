package com.chae.promo.order.service;

import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;

public interface OrderService {
    OrderResponse.Purchase placeOrder(OrderRequest.Purchase request, String userId);
    OrderResponse.OrderSummary createOrder(OrderRequest.Create request, String userId);
}
