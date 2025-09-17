-- 'orders' 테이블의 'modified_at' 컬럼 이름을 'updated_at'으로 변경
ALTER TABLE orders RENAME COLUMN modified_at TO updated_at;
ALTER TABLE orders MODIFY customer_id int unsigned;