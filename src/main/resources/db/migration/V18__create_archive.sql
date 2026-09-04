-- 기록 보관(보관함). 화면 FDR-2-MODAL-02-0 「현재까지 장부를 모두 보관할까요?」 → ETC-2-PAGE-06-0 「보관함」.
--
-- 보고서(report)와 같은 스냅샷 구조다. 다른 점은 보고서가 원본을 남겨 둔 채 복사본을 만드는 반면,
-- 보관은 복사한 뒤 원본 폴더·장부·내역을 지워 모임을 비운다는 것이다("보관 후 장부는 수정이 불가합니다").
CREATE TABLE archive
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    group_id      BIGINT       NOT NULL,
    title         VARCHAR(20)  NOT NULL,
    -- 카드에 "YYYY.MM.DD - YYYY.MM.DD" 로 찍는 값. 담긴 내역의 실제 최소·최대 발생일이다.
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    total_income  BIGINT       NOT NULL,
    total_expense BIGINT       NOT NULL,
    entry_count   BIGINT       NOT NULL,
    ledger_count  BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_archive_group (group_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE archive_ledger
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    archive_id    BIGINT       NOT NULL,
    -- 최상위 영역에 있던 장부는 폴더명이 없다.
    folder_name   VARCHAR(20)  NULL,
    ledger_name   VARCHAR(20)  NOT NULL,
    budget        BIGINT       NULL,
    total_income  BIGINT       NOT NULL,
    total_expense BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_archive_ledger_archive FOREIGN KEY (archive_id) REFERENCES archive (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE archive_entry
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    archive_ledger_id BIGINT       NOT NULL,
    entry_type        VARCHAR(20)  NOT NULL,
    title             VARCHAR(20)  NOT NULL,
    amount            BIGINT       NOT NULL,
    occurred_on       DATE         NOT NULL,
    memo              VARCHAR(30)  NULL,
    -- 승인 대기 상태로 보관된 내역도 그대로 남긴다. 보관 시점의 사실이 그대로 기록이다.
    approval_status   VARCHAR(20)  NOT NULL,
    created_by_name   VARCHAR(10)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_archive_entry_ledger FOREIGN KEY (archive_ledger_id) REFERENCES archive_ledger (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
