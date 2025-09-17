-- 주문 테이블
CREATE TABLE `orders`
(
    `id`          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    `public_id`   VARCHAR(36)     NOT NULL COMMENT '외부 공개용 주문 ID (UUID)',
    `customer_id` INT UNSIGNED    NOT NULL COMMENT '구매자 ID',

    -- 비회원 정보를 저장하기 위한 컬럼들
    `orderer_name`    VARCHAR(100)   NOT NULL COMMENT '주문자 이름',

    `total_price` DECIMAL(13, 0)  NOT NULL COMMENT '총 결제 금액',
    `status`      VARCHAR(20)     NOT NULL COMMENT '주문 상태',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문일자',
    `modified_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일자',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ux_public_id` (`public_id`),
    KEY `idx_customer_id` (`customer_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;