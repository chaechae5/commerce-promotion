package com.chae.promo.order.event;

public interface OrderStockProcessor {
    void processStockDecrease(OrderPlacedEvent event);

}
