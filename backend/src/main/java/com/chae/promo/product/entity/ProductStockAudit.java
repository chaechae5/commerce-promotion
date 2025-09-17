package com.chae.promo.product.entity;

import com.chae.promo.common.entity.CreatedAtBase;
import com.chae.promo.order.entity.UserType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_stock_audit")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductStockAudit extends CreatedAtBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeType changeType;

    @Column(nullable = false)
    private long changeQuantity;

    @Column(nullable = false)
    private long currentStock;

    @Column(length = 36)
    private String changedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserType userType;

    @Column(length = 36)
    private String orderPublicId;

    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(columnDefinition = "TEXT")
    private String description;

    public enum ChangeType {
        DECREASE, INCREASE, INITIAL, CANCELED
    }

}