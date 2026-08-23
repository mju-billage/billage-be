-- 대시보드 최근 내역(EntryRepository.findRecentByGroupId) 전용 인덱스.
-- 쿼리는 `where group_id = ? order by occurred_on desc, id desc` 인데
-- 기존 idx_entry_group (group_id, approval_status) 에는 정렬 컬럼이 없어,
-- 모임의 전체 내역을 읽고 filesort 한 뒤 상위 N 건만 버리고 남겼다.
-- 정렬 방향까지 맞춘 내림차순 인덱스로 정렬 없이 앞에서 N 건만 읽는다(MySQL 8.0+ 내림차순 인덱스).
-- idx_entry_group 은 모임 단위 집계·삭제에 계속 쓰이므로 그대로 둔다.
CREATE INDEX idx_entry_group_occurred ON entry (group_id, occurred_on DESC, id DESC);
