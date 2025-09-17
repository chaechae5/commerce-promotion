package com.chae.promo.order.entity;

import com.chae.promo.common.entity.CreatedAtBase;
import com.chae.promo.product.entity.Goods;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_goods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderGoods extends CreatedAtBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order; // 주문


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods; // 굿즈

}