-- 주문 아이템 테이블
CREATE TABLE `order_items`
(
    `id`         INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    `order_id`   INT UNSIGNED   NOT NULL COMMENT '주문 ID',
    `product_id` INT UNSIGNED   NOT NULL COMMENT '상품 ID',
    `quantity`   INT UNSIGNED   NOT NULL COMMENT '상품 개수',
    `unit_price` DECIMAL(13, 0) NOT NULL COMMENT '주문 당시 상품 단가',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
