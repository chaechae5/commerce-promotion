package com.chae.promo.payment.repository;

import com.chae.promo.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long>{

}
