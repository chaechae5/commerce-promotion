package com.chae.promo.product.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTime{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code; // 상품 관리 코드

    @Column(nullable = false)
    private String name; // 상품명

    @Column(nullable = false, precision = 13)
    private BigDecimal price; // 상품 가격

    @Setter
    @Column(nullable = false)
    private long stockQuantity; // 재고 수량

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status; // 상품 상태 (FOR_SALE, SOLD_OUT 등)

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    public Product(Long id, String code, String name, BigDecimal price, long stockQuantity, ProductStatus status, Long version) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.version = version;
    }
}