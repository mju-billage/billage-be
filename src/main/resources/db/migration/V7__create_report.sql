-- 보고서(Report) — 생성 시점의 장부·내역을 복사한 스냅샷(고정본).
-- 원본 장부·내역이 수정·삭제되어도 보고서는 변하지 않아야 하므로
-- ledger_id / entry_id 는 참고용 값일 뿐 FK 를 걸지 않고 NULL 도 허용한다.
-- 표시 금액·문구는 언제나 스냅샷 컬럼 값을 사용한다.
CREATE TABLE report (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    group_id      BIGINT      NOT NULL,
    title         VARCHAR(20) NOT NULL,
    start_date    DATE        NOT NULL,
    end_date      DATE        NOT NULL,
    -- 목록에서 자식 테이블을 읽지 않고 요약을 보여주기 위한 스냅샷 집계값.
    -- 잔액은 저장하지 않고 total_income - total_expense 로 계산한다.
    total_income  BIGINT      NOT NULL,
    total_expense BIGINT      NOT NULL,
    entry_count   BIGINT      NOT NULL,
    ledger_count  BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_group FOREIGN KEY (group_id) REFERENCES group_space (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 모임별 보고서 목록(생성일 최신순) 조회용
CREATE INDEX idx_report_group ON report (group_id, created_at, id);

CREATE TABLE report_ledger (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    report_id     BIGINT      NOT NULL,
    -- 원본 장부 참고용. 장부가 삭제되어도 스냅샷은 유지된다.
    ledger_id     BIGINT      NULL,
    ledger_name   VARCHAR(20) NOT NULL,
    total_income  BIGINT      NOT NULL,
    total_expense BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_ledger_report FOREIGN KEY (report_id) REFERENCES report (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_report_ledger_report ON report_ledger (report_id, id);

CREATE TABLE report_entry (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    report_ledger_id BIGINT      NOT NULL,
    -- 원본 내역 참고용. 내역이 삭제되어도 스냅샷은 유지된다.
    entry_id         BIGINT      NULL,
    type             VARCHAR(20) NOT NULL,
    title            VARCHAR(20) NOT NULL,
    amount           BIGINT      NOT NULL,
    occurred_on      DATE        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_entry_report_ledger FOREIGN KEY (report_ledger_id) REFERENCES report_ledger (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 보고서 상세에서 장부별 내역을 발생일 순으로 읽기 위한 인덱스
CREATE INDEX idx_report_entry_ledger ON report_entry (report_ledger_id, occurred_on, id);
