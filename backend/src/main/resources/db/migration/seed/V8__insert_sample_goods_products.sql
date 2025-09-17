-- 테스트용 상품(products) 데이터 삽입
INSERT INTO products(code, name, price, stock_quantity, status, created_at, updated_at)
VALUES ('P00000001', '대용량 저당 단백질 쉐이크', 3900, 100, 'FOR_SALE', NOW(), NOW()),
       ('P00000002', '조앤프렌즈 인형', 29900, 500, 'FOR_SALE', NOW(), NOW());

-- 이벤트용 굿즈(goods) 데이터 삽입
INSERT INTO goods(name, quantity, created_at)
VALUES ('[이벤트] 한정판 라부부 키링', 100, NOW());