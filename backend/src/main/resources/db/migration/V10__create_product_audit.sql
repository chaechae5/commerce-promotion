-- 상품 재고 audit 테이블 생성

CREATE TABLE product_stock_audit (
     id int unsigned AUTO_INCREMENT PRIMARY KEY,
     product_id int unsigned NOT NULL,
     change_type VARCHAR(20) NOT NULL,
     change_quantity int unsigned NOT NULL,
     current_stock int unsigned NOT NULL,
     changed_by_user_id VARCHAR(36),
     user_type varchar(20) NOT NULL,
     order_public_id VARCHAR(36),
     event_id VARCHAR(36) NOT NULL,
     description TEXT,
     created_at DATETIME(6) NOT NULL,
     INDEX idx_product_id (product_id)
);
