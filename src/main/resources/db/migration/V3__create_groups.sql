-- 모임(Group)과 모임 관리 권한(GroupManager).
-- 권한 주체는 사용자↔모임 관계인 group_managers 이며, 모임원 명단(group_members)과는 별개다.
-- `groups`/`GROUPS`는 MySQL 8.0 예약어이므로 백틱으로 인용한다.
CREATE TABLE `groups` (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    invite_code VARCHAR(16)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_groups_invite_code UNIQUE (invite_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE group_managers (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_managers_group_user UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_managers_group FOREIGN KEY (group_id) REFERENCES `groups` (id),
    CONSTRAINT fk_group_managers_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- "내 모임" 조회(사용자 기준) 인덱스
CREATE INDEX idx_group_managers_user ON group_managers (user_id);
