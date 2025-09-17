package com.chae.promo.product.entity;

import com.chae.promo.common.entity.CreatedAtBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "goods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goods extends CreatedAtBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 굿즈 상품명

    @Column(nullable = false)
    private long quantity; // 굿즈 재고 수량

}