package com.chae.promo.coupon.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Getter
public class Coupon extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 21)
    private String publicId;

    @Column(nullable = false, unique = true)
    private String code;

    private String name;
    private String description;
    @Column(nullable = false)
    private Integer totalQuantity = 0;
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private LocalDateTime expireDate;

    private Integer validDays;

    @Builder
    public Coupon(Long id, String publicId, String code, String name, String description, Integer totalQuantity, LocalDateTime startDate, LocalDateTime endDate, LocalDateTime expireDate, Integer validDays) {
        this.id = id;
        this.publicId = publicId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.totalQuantity = totalQuantity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.expireDate = expireDate;
        this.validDays = validDays;
    }

}
