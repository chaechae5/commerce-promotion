package com.chae.promo.product.util;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.entity.ProductStatus;
import com.chae.promo.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductValidator {

    private final ProductRepository productRepository;


    public <T> Map<String, Product> getAndValidateProductMap(
            List<T> items,
            Function<T, String> codeExtractor,
            Function<T, Long> quantityExtractor) {

        List<String> productCodes = items.stream()
                .map(codeExtractor)
                .toList();

        Map<String, Product> productMap = productRepository.findByCodeIn(productCodes)
                .stream()
                .collect(Collectors.toMap(Product::getCode, product -> product));

        for (T item : items) {
            Product product = productMap.get(codeExtractor.apply(item));

            if (product == null || !productMap.containsKey(codeExtractor.apply(item))) {
                log.warn("존재하지 않는 상품 정보. code:{}", codeExtractor.apply(item));
                throw new CommonCustomException(CommonErrorCode.PRODUCT_NOT_FOUND);
            }

            if (product.getStatus() != ProductStatus.FOR_SALE) {
                log.warn("판매중이 아닌 상품. code:{}, status:{}", codeExtractor.apply(item), product.getStatus());
                throw new CommonCustomException(CommonErrorCode.PRODUCT_NOT_FOR_SALE);
            }

            if(product.getStockQuantity() < quantityExtractor.apply(item) ) {
                log.warn("재고 부족 상품. code:{}, 현재 재고 :{}, 요청 재고:{}",
                        codeExtractor.apply(item),  product.getStockQuantity(), quantityExtractor.apply(item));
                throw new CommonCustomException(CommonErrorCode.INSUFFICIENT_STOCK);
            }
        }
        return productMap;
    }
}
