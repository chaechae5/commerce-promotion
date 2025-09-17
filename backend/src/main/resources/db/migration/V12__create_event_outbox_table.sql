CREATE TABLE IF NOT EXISTS event_outbox (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id       VARCHAR(36) NOT NULL UNIQUE,             -- 멱등키(카프카 key로도 사용 권장)
    type           VARCHAR(20) NOT NULL,                    -- 'ORDER_PLACED' 등
    aggregate_id   VARCHAR(36) NOT NULL,                    -- 예: orderPublicId
    payload_json   JSON NOT NULL,                           -- 이벤트 본문(JSON 직렬화)
    status         VARCHAR(16) NOT NULL,                    -- 'PENDING' | 'SENT' | 'FAILED'
    retry_count    INT NOT NULL,
    next_retry_at  DATETIME(3) NOT NULL,
    created_at     DATETIME(3) NOT NULL,
    updated_at     DATETIME(3) NOT NULL,
    last_error     VARCHAR(512),
    KEY idx_status_next (status, next_retry_at, id),        -- 기본 재시도 스캔 인덱스
    KEY idx_next (next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
