package com.chae.promo.product.repository;

import com.chae.promo.product.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductBulkRepository {
    private final JdbcTemplate jdbcTemplate;

    public int bulkUpdateStock(List<ProductRequest.StockBulkUpdate> updates) {
        String sql = """
            UPDATE products 
            SET stock_quantity = stock_quantity - ?,
                version = version + 1
            WHERE id = ? AND version = ? AND stock_quantity >= ?
            """;

        List<Object[]> batchArgs = updates.stream()
                .map(update -> new Object[]{
                        update.getDecreasedStock(),
                        update.getProductId(),
                        update.getVersion(),
                        update.getDecreasedStock()
                })
                .collect(Collectors.toList());
        int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);

        // 실패한 업데이트 검증
        for (int i = 0; i < results.length; i++) {
            if (results[i] == 0) {
                ProductRequest.StockBulkUpdate failedUpdate = updates.get(i);
                log.warn("재고 업데이트 실패 - productId:{}, requestedVersion:{}, requestedDecreasedStock:{}",
                        failedUpdate.getProductId(), failedUpdate.getVersion(), failedUpdate.getDecreasedStock());

                throw new ObjectOptimisticLockingFailureException(
                        "재고 업데이트 실패 - productId: ", failedUpdate.getProductId());
            }
        }

        return Arrays.stream(results).sum();
    }

}
