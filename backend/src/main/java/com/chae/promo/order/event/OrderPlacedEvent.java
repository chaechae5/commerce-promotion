package com.chae.promo.order.event;

import com.chae.promo.order.entity.UserType;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPlacedEvent {
    private String eventId; // 이벤트 ID
    private String userId; // 사용자 ID
    private UserType userType; // 사용자 유형 (MEMBER, GUEST)
    private String orderPublicId;  // 주문 공개 ID
    private List<Item> items; // 상품 아이템 리스트로 변경


    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Item {
        private String productCode; // 상품 코드
        private long decreasedStock; // 감소된 재고 수량

    }
}
