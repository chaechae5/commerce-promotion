package com.chae.promo.coupon.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue")
@Getter
@NoArgsConstructor
public class CouponIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // DB 내부 기본 키

    @Column(unique = true, nullable = false, length = 36) // UUID는 36자 (하이픈 포함)
    private String publicId; // 외부에 노출할 고유 식별자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime expireAt;

    @Enumerated(EnumType.STRING)
    private CouponIssueStatus status;
    private LocalDateTime usedDate;


    @Builder
    public CouponIssue(Long id,
                       Coupon coupon,
                       String userId,
                       LocalDateTime issuedAt,
                       LocalDateTime expireAt,
                       CouponIssueStatus status,
                       LocalDateTime usedDate,
                       String publicId) {
        this.id = id;
        this.coupon = coupon;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expireAt = expireAt;
        this.status = status;
        this.usedDate = usedDate;
        this.publicId = publicId;
    }
}
