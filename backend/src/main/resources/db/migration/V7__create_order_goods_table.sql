-- 주문별 지급 굿즈 이력 테이블
CREATE TABLE `order_goods`
(
    `id`         INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`   INT UNSIGNED NOT NULL COMMENT '주문 ID',
    `goods_id`   INT UNSIGNED NOT NULL COMMENT '굿즈 ID',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '지급일자',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_goods_id` (`goods_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;