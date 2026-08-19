-- 수입·지출 내역(Entry).
-- 회계 이력의 행위자는 납부 명단(Member)이 아니라 가입 사용자(User) 기준으로 기록한다.
-- 작성자·승인자 이름은 그 시점 값을 함께 보존해, 이후 해당 관리자가 모임을 탈퇴해도 과거 이력 표시가 깨지지 않게 한다.
CREATE TABLE entry (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    group_id             BIGINT      NOT NULL,
    ledger_id            BIGINT      NOT NULL,
    type                 VARCHAR(20) NOT NULL,
    title                VARCHAR(20) NOT NULL,
    -- 원화, 항상 양수(최대 999,999,999). 수입·지출 구분은 type 으로만 한다.
    amount               BIGINT      NOT NULL,
    occurred_on          DATE        NOT NULL,
    memo                 VARCHAR(30) NULL,
    approval_status      VARCHAR(20) NOT NULL,
    created_by_user_id   BIGINT      NOT NULL,
    created_by_name      VARCHAR(10) NOT NULL,
    approved_by_user_id  BIGINT      NULL,
    approved_by_name     VARCHAR(10) NULL,
    approved_at          DATETIME(6) NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_entry_group FOREIGN KEY (group_id) REFERENCES group_space (id),
    CONSTRAINT fk_entry_ledger FOREIGN KEY (ledger_id) REFERENCES ledger (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 장부별 내역 목록(발생일 최신순) 조회용
CREATE INDEX idx_entry_ledger_occurred ON entry (ledger_id, occurred_on, id);
-- 장부 집계(승인분 합계)와 승인 대기 수 조회용
CREATE INDEX idx_entry_ledger_status ON entry (ledger_id, approval_status);
-- 모임 단위 집계·삭제용
CREATE INDEX idx_entry_group ON entry (group_id, approval_status);
