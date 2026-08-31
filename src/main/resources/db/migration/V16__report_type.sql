-- 보고서 유형. 화면명세는 보고서를 두 갈래로 나누고 각각 입력받는 항목이 다르다.
--
--   장부별(BY_LEDGER) : 제목 + 장부(다중) + 구분(전체/수입/지출).  기간을 받지 않는다.
--   기간별(BY_PERIOD) : 제목 + 기간.                              장부를 받지 않는다.
--
-- 기존 API 는 넷을 전부 필수로 받아 어느 화면도 그대로 만족시키지 못했다.
ALTER TABLE report
    ADD COLUMN report_type VARCHAR(20) NULL AFTER title,
    -- 보고서에 담은 내역의 구분. NULL 이면 전체(수입+지출)다.
    ADD COLUMN entry_type  VARCHAR(20) NULL AFTER report_type;

-- 기존 보고서는 장부를 명시해 만들었으므로 장부별로 본다.
UPDATE report
SET report_type = 'BY_LEDGER'
WHERE report_type IS NULL;

ALTER TABLE report
    MODIFY COLUMN report_type VARCHAR(20) NOT NULL;

-- 기간별 보고서의 잔액 흐름 카드(캐러셀 2페이지)가 쓰는 값.
--   시작 잔액: 기간 시작일 '직전'까지 누적된 대상 장부들의 잔액 합
--   최종 잔액: 시작 잔액 + 기간 내 수입 - 기간 내 지출
-- 스냅샷이라 저장한다. 원본 내역이 나중에 바뀌어도 보고서는 그대로여야 한다.
-- 장부별 보고서에는 해당 화면이 없어 NULL 이다.
ALTER TABLE report
    ADD COLUMN opening_balance BIGINT NULL,
    ADD COLUMN closing_balance BIGINT NULL;

-- 목록이 유형별 탭(장부별 / 기간별)으로 나뉜다.
CREATE INDEX idx_report_group_type ON report (group_id, report_type, created_at);
