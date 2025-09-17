package com.chae.promo.order.mapper;

import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    /**
     * Order 엔티티를 OrderResponse.Purchase DTO로 변환합
     *
     * @param order 변환할 Order 엔티티
     * @return 변환된 OrderResponse.Purchase DTO
     */
    public OrderResponse.Purchase toPurchaseResponse(Order order) {
        return OrderResponse.Purchase.builder()
                .customerId(order.getCustomerId())
                .publicId(order.getPublicId())
                .ordererName(order.getOrdererName())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus().name())
                .orderItems(
                        order.getOrderItems().stream()
                                .map(this::toPurchaseItemResponse) // 매핑 메서드 호출
                                .collect(Collectors.toList())
                )
                .build();
    }

    /**
     * OrderItem 엔티티를 OrderResponse.PurchaseItem DTO로 변환
     *
     * @param orderItem 변환할 OrderItem 엔티티
     * @return 변환된 OrderResponse.PurchaseItem DTO
     */
    private OrderResponse.PurchaseItem toPurchaseItemResponse(OrderItem orderItem) {
        return OrderResponse.PurchaseItem.builder()
                .productCode(orderItem.getProduct().getCode())
                .productName(orderItem.getProduct().getName())
                .price(orderItem.getUnitPrice())
                .quantity(orderItem.getQuantity())
                .totalPrice(orderItem.getUnitPrice().multiply(new BigDecimal(orderItem.getQuantity())))
                .build();
    }
}
