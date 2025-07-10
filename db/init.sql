CREATE DATABASE IF NOT EXISTS promo_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE promo_db;

CREATE TABLE IF NOT EXISTS coupons(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    description VARCHAR(255),
    total_quantity INT NOT NULL DEFAULT 0,
    start_date DATETIME,
    end_date DATETIME,
    expire_date DATETIME,
    valid_days INT DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS coupon_issue(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME,
    status VARCHAR(20) DEFAULT 'ISSUED',
    used_date DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT uq_coupon_user UNIQUE (coupon_id, user_id)
);

INSERT INTO coupons (code, name, description, total_quantity, start_date, end_date, expire_date)
VALUES
  ('LABUBUISCOMMING', '라부부 선착순 굿즈 쿠폰', '라부부 선착순 굿즈 쿠폰', 500, 
   '2025-07-10 00:00:00', '2025-07-15 17:00:00', '2025-07-15 23:59:59');

INSERT INTO coupons (code, name, description, total_quantity, start_date, end_date,valid_days)
VALUES
  ('WELCOME2025', '웰컴 쿠폰', '신규 회원 웰컴 쿠폰', 1000, '2025-07-10 00:00:00', '2025-07-31 23:59:59',7),
  ('SUMMER2025', '여름 이벤트 쿠폰', '여름 한정 10% 할인', 500, '2025-07-15 00:00:00', '2025-08-15 23:59:59',7),
  ('LIMITED50', '선착순 50명 쿠폰', '50명 한정 무료배송 쿠폰', 50, '2025-07-12 00:00:00', '2025-07-20 23:59:59',7);

