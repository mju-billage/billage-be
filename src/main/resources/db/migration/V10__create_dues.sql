-- 회비(Dues)와 대상자별 납부 상태(DuesMember).
-- 납부 대상은 가입 사용자가 아니라 회비 납부 명단(member) 기준이다.
CREATE TABLE dues (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    group_id           BIGINT      NOT NULL,
    title              VARCHAR(20) NOT NULL,
    -- 1인당 금액. 생성 후 수정할 수 없다(DUES_AMOUNT_IMMUTABLE).
    amount             BIGINT      NOT NULL,
    due_date           DATE        NOT NULL,
    -- OPEN(진행 중) | CLOSED(마감)
    status             VARCHAR(20) NOT NULL,
    -- 마감 시 수입 내역을 만들 장부. 장부는 자유롭게 삭제될 수 있어 FK 를 걸지 않는다
    -- (삭제된 장부로 마감하려 하면 LEDGER_NOT_FOUND).
    ledger_id          BIGINT      NULL,
    -- 마감 시 만들어진 수입 내역. 마감 즉시 회비와 내역은 서로 독립된 데이터가 되므로
    -- FK 없이 참고용으로만 들고 있는다 — 회비를 지워도 내역은 장부에 남는다(기획 확정).
    generated_entry_id BIGINT      NULL,
    closed_at          DATETIME(6) NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_dues_group FOREIGN KEY (group_id) REFERENCES group_space (id),
    CONSTRAINT ck_dues_amount CHECK (amount BETWEEN 1 AND 999999999)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 모임별 회비 목록(상태 필터 + 마감일 정렬)
CREATE INDEX idx_dues_group ON dues (group_id, status, due_date);

CREATE TABLE dues_member (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    dues_id   BIGINT      NOT NULL,
    member_id BIGINT      NOT NULL,
    -- UNPAID(미납) | PAID(납부 완료). 부분·초과 납부는 지원하지 않는다.
    status    VARCHAR(20) NOT NULL,
    paid_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 같은 모임원을 한 회비에 두 번 넣을 수 없다(중복 납부 반영 방지).
    CONSTRAINT uk_dues_member UNIQUE (dues_id, member_id),
    CONSTRAINT fk_dues_member_dues FOREIGN KEY (dues_id) REFERENCES dues (id),
    CONSTRAINT fk_dues_member_member FOREIGN KEY (member_id) REFERENCES group_member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 납부 대상 목록과 납부/미납 집계용
CREATE INDEX idx_dues_member_status ON dues_member (dues_id, status);
