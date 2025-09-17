package com.chae.promo.payment.repository;

import com.chae.promo.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>{

    Optional<Payment> findByOrderId(long orderId);
}
