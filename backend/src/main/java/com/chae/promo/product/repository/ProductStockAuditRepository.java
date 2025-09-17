package com.chae.promo.product.repository;

import com.chae.promo.product.entity.ProductStockAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductStockAuditRepository extends JpaRepository<ProductStockAudit, Long>  {

}
