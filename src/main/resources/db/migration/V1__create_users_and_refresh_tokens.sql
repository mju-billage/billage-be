CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE refresh_tokens (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    user_id              BIGINT      NOT NULL,
    token_hash           VARCHAR(64) NOT NULL,
    family_id            VARCHAR(36) NOT NULL,
    device_id            VARCHAR(255) NULL,
    expires_at           DATETIME(6) NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    last_used_at         DATETIME(6) NULL,
    revoked_at           DATETIME(6) NULL,
    replaced_by_token_id BIGINT       NULL,
    revoke_reason        VARCHAR(20)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 재사용 감지 시 familyId 단위로 활성 토큰을 폐기하기 위한 인덱스
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
