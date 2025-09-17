-- orders 테이블에 product_name 컬럼 추가
ALTER TABLE orders ADD COLUMN product_name varchar(100) not null;
