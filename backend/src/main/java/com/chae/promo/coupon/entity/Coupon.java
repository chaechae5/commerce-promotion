package com.chae.promo.coupon.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@EntityListeners(AuditingEntityListener.class)
@Getter
public class Coupon extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;
    private String name;
    private String description;
    @Column(nullable = false)
    private Integer totalQuantity = 0;
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
