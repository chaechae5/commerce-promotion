package com.chae.promo.order.repository;

import com.chae.promo.order.entity.OrderItem;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
    select coalesce(sum(i.quantity), 0)
    from OrderItem i
    join i.order o
    where o.publicId = :publicId
  """)
    long sumQuantityByOrderPublicId(@Param("publicId") String publicId);
}
