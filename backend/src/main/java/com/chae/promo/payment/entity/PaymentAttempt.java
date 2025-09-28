package com.chae.promo.payment.entity;

import com.chae.promo.common.entity.CreatedAtBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentAttempt extends CreatedAtBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;


    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentMethod paymentMethod;

    @Column(length = 255)
    private String failureMessage;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    // ====== 편의 메서드 ======

    public static PaymentAttempt success(Payment payment,
                                         BigDecimal amount,
                                         PaymentMethod method,
                                         LocalDateTime attemptedAt) {
        return PaymentAttempt.builder()
                .payment(payment)
                .status(PaymentStatus.COMPLETED)
                .amount(amount)
                .paymentMethod(method)
                .attemptedAt(attemptedAt)
                .build();
    }

    public static PaymentAttempt failure(Payment payment,
                                         BigDecimal amount,
                                         PaymentMethod method,
                                         String failureMessage,
                                         LocalDateTime attemptedAt) {
        return PaymentAttempt.builder()
                .payment(payment)
                .status(PaymentStatus.FAILED)
                .amount(amount)
                .paymentMethod(method)
                .failureMessage(failureMessage)
                .attemptedAt(attemptedAt)
                .build();
    }
}
