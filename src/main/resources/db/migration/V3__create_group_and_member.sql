-- 모임(GroupSpace) · 모임 관리자 관계(GroupMembership) · 납부 관리용 모임원 명단(Member) · 초대 코드.
-- Group 은 SQL 예약어라 테이블명은 group_space 를 사용한다.
--
-- 관리자와 모임원 명단은 별개 개념이다(기획 확정).
--   group_membership : 가입 사용자 ↔ 모임의 권한 관계(OWNER/MEMBER). 로그인 주체.
--   group_member     : 회비 납부 대상 명단. 이름만 가지며 계정과 자동 연결하지 않는다.
CREATE TABLE group_space (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(20) NOT NULL,
    description VARCHAR(50) NULL,
    created_by  BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_space_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE group_membership (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_membership_group_user UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_membership_group FOREIGN KEY (group_id) REFERENCES group_space (id),
    CONSTRAINT fk_group_membership_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 내 모임 목록 조회용
CREATE INDEX idx_group_membership_user ON group_membership (user_id);

-- 납부 관리 대상 명단. 동명이인이 가능하므로 이름에 유니크 제약을 두지 않는다.
CREATE TABLE group_member (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    name       VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES group_space (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 모임원 명단 조회용(이름 오름차순)
CREATE INDEX idx_group_member_group_name ON group_member (group_id, name);

-- 모임 관리자 참여용 초대 코드. 모임원 명단을 만들지 않는다.
CREATE TABLE group_invitation (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    code       VARCHAR(20) NOT NULL,
    created_by BIGINT      NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_invitation_code UNIQUE (code),
    CONSTRAINT fk_group_invitation_group FOREIGN KEY (group_id) REFERENCES group_space (id),
    CONSTRAINT fk_group_invitation_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_group_invitation_group ON group_invitation (group_id);
