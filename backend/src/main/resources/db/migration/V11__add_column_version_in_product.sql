-- 상품 테이블 version 컬럼 생성
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;