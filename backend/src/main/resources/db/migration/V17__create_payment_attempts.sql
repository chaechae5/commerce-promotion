-- 결제 정보 테이블 추가

CREATE TABLE payment_attempts(
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_id INT UNSIGNED NOT NULL COMMENT '결제 ID',
  payment_method VARCHAR(20) NOT NULL COMMENT '결제 수단',
  amount DECIMAL(13, 2) NOT NULL COMMENT '결제 금액',
  status VARCHAR(20) NOT NULL COMMENT '결제 상태',
  failure_message VARCHAR(255) COMMENT '결제 메세지',
  attempted_at DATETIME NOT NULL COMMENT '결제 시도 일자',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일자'
);