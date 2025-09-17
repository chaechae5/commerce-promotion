package com.chae.promo.order.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String publicId; // 외부 공개용 ID (UUID)

    @Column
    private Long customerId; // 회원 ID (비회원일 경우 NULL)

    @Column(nullable = false, length = 100)
    private String ordererName; // 주문자 이름 (비회원일 때 주문자이름 저장)

    @Column(nullable = false, precision = 13)
    private BigDecimal totalPrice; // 총 결제 금액

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status; // 주문 상태

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>(); // 주문 상품 목록

    @Column(nullable = false, length = 100)
    private String productName;

    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);

        // 양방향 연관관계 설정
        orderItem.setOrder(this);
    }

    // 총액 업데이트 메서드
    public void updateTotalPrice(BigDecimal totalPrice){
        this.totalPrice = totalPrice;
    }

    // 주문 상태 변경 메서드
    public void markPaid() { this.status = OrderStatus.PAID; }
    public void markPendingPayment() { this.status = OrderStatus.PENDING_PAYMENT; }
}