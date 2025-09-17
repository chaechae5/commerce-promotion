package com.chae.promo.product.dto;

import lombok.*;

public class ProductRequest {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StockBulkUpdate{
        long productId; // 상품 ID
        long decreasedStock; // 감소된 재고 수량
        long version;
    }
}
