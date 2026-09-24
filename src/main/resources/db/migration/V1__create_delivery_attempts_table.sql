CREATE TABLE delivery_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    event_id CHAR(36) NOT NULL,
    subscription_id CHAR(36) NOT NULL,
    target_url VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payload JSON NOT NULL,
    attempt_count INT NOT NULL,
    last_attempt_at DATETIME,
    created_at DATETIME NOT NULL
)