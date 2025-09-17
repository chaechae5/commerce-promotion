package com.chae.promo.order.service.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StockRedisKeyManager {

    @Value("${app.redis.prefix:}")
    private String prefix;

    @Value("${app.redis.use-hashtag:true}")
    private boolean useHashtag;

    // 특정 상품의 재고 키 (자료구조: String)
    private static final String PRODUCT_STOCK_KEY = "product:stock:%s"; //  %s = productCode

    /**
     * 상품 재고 Key 생성
     * @param productCode 상품 코드
     * @return Redis 상품 재고 Key
     */
    public String getProductStockKey(String productCode) {
        return String.format(PRODUCT_STOCK_KEY, productCode);
    }

    // ──[ 확장 키 ]──────────────────────────────────────────────────────────

    private String pfx() {
        return (prefix == null || prefix.isBlank()) ? "" : prefix + ":";
    }

    private String tag(String sku) { return useHashtag ? "{" + sku + "}" : sku; }


    /** 실제 가용 재고 */
    public String available(String sku) {
        return pfx() + "stock:" + tag(sku) + ":available";
    }

    /** 예약 누적량 */
    public String reserved(String sku) {
        return pfx() + "stock:" + tag(sku) + ":reserved";
    }

    /** 주문별 hold 키 (값=qty, TTL 부여) */
    public String hold(String sku, String orderId) {
        return pfx() + "hold:" + tag(sku) + ":" + orderId;
    }

    /** 만료 인덱스(ZSET) */
    public String holdIndex(String sku) {
        return pfx() + "hold_index:" + tag(sku);
    }
}

