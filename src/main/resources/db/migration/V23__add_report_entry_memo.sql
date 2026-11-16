-- 보고서 내역 스냅샷에 메모를 더한다. 생성 시점의 원본 내역 메모를 복사하며 이후 원본이 바뀌어도 그대로다.
-- 이미 만들어진 보고서의 행은 메모를 알 수 없어 NULL 로 둔다.
ALTER TABLE report_entry
    ADD COLUMN memo VARCHAR(30) NULL AFTER occurred_on;
