-- 앱 안 알림(푸시 없음). 알림은 이벤트가 생길 때 받는 사람마다 한 행씩 쌓는다.
-- 받는 사람이 그 카테고리를 꺼 두었으면 처음부터 만들지 않는다(조회 때 거르지 않음).
-- 회원 탈퇴·모임 삭제 시 FK ON DELETE CASCADE 로 함께 지워진다.
-- 대상(내역·회비 등)이 지워져도 알림은 남는다 — 앱이 열면 404 를 받는다.
CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    group_id    BIGINT       NULL,
    category    VARCHAR(20)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        VARCHAR(255) NOT NULL,
    target_type VARCHAR(20)  NULL,
    target_id   BIGINT       NULL,
    read_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_group FOREIGN KEY (group_id) REFERENCES group_space (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 내 알림 목록(최신순)과 안 읽은 알림 존재 여부 조회용
CREATE INDEX idx_notification_user_created ON notification (user_id, created_at, id);
CREATE INDEX idx_notification_user_read ON notification (user_id, read_at);

-- 알림 설정. 행이 없으면 기본값(야간 수신만 꺼짐)이다 — 가입 때 미리 만들지 않는다.
-- 마케팅 수신은 여기 두지 않고 users.marketing_agreed_at 하나로 관리한다(가입 동의와 어긋나지 않게).
CREATE TABLE notification_setting (
    user_id           BIGINT      NOT NULL,
    group_activity    BOOLEAN     NOT NULL,
    approval          BOOLEAN     NOT NULL,
    dues              BOOLEAN     NOT NULL,
    notice_and_update BOOLEAN     NOT NULL,
    night_time        BOOLEAN     NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
