-- 회비 납부 시작일. 화면명세는 회비 기간을 시작일~마감일 '범위'로 받고,
-- 납부 현황 카드를 납부 예정 / 진행 중 / 마감 세 가지로 나눈다(DUE-1-PAGE-01-0).
--
-- 상태 컬럼은 그대로 OPEN | CLOSED 두 값만 저장한다. '납부 예정'은 시작일과 오늘을 비교해
-- 읽는 시점에 파생시킨다 — 별도 상태로 저장하면 시작일이 되는 순간 OPEN 으로 바꿔 줄
-- 배치가 필요해지고, 그 배치가 밀리면 화면과 DB 가 어긋난다.
ALTER TABLE dues
    ADD COLUMN start_date DATE NULL AFTER amount;

-- 기존 회비는 시작일 개념 없이 만들어졌다. 생성일을 시작일로 채우면 전부 '진행 중'으로 잡혀
-- 지금 화면과 같은 상태가 유지된다.
UPDATE dues
SET start_date = DATE(created_at)
WHERE start_date IS NULL;

ALTER TABLE dues
    MODIFY COLUMN start_date DATE NOT NULL;

-- 시작일 <= 마감일 검증은 애플리케이션에서 한다. 기존 데이터 중 생성일이 마감일보다 늦은
-- 회비가 있을 수 있어(마감일을 과거로 잡아 만든 경우) DB 제약으로 걸면 마이그레이션이 실패한다.

-- 목록 인덱스(idx_dues_group)는 그대로 둔다. group_id 가 선두라 fk_dues_group 이 이 인덱스에 의존하고 있어
-- 교체하려면 FK 를 떼었다 붙여야 한다. 정렬에 start_date 가 끼어들지만 모임당 회비는 많아야 수십 건이라
-- 남는 정렬 비용이 인덱스를 갈아엎을 만큼 크지 않다.
