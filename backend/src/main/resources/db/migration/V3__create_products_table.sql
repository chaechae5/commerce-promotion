CREATE TABLE `products`
(
    `id`             INT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT '상품 고유 ID',
    `code`           VARCHAR(50)    NOT NULL COMMENT '상품 코드',
    `name`           VARCHAR(255)   NOT NULL COMMENT '상품명',
    `price`          DECIMAL(13, 0) NOT NULL COMMENT '상품 가격',
    `stock_quantity` INT UNSIGNED   NOT NULL COMMENT '재고 수량',
    `status`         VARCHAR(20)    NOT NULL DEFAULT 'FOR_SALE' COMMENT '상품 상태 (FOR_SALE, SOLD_OUT 등)',
    `created_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    `updated_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ux_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;