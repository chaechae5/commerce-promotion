package com.chae.promo.product.repository;

import com.chae.promo.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCodeIn(List<String> codes);
}
