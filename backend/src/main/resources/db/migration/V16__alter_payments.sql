-- 결제 정보 테이블 추가
ALTER TABLE payments MODIFY COLUMN payment_key VARCHAR(255) UNIQUE COMMENT 'PG 결제 키';