-- 금액은 원화 양수이며 상한은 999,999,999 원이다(기획 글로벌 정책).
-- 애플리케이션에서 @Positive · @Max 로 검증하지만, 회계 데이터라 DB 에서도 한 번 더 막는다.
-- 수입·지출 구분은 type 으로만 하므로 음수 금액은 어떤 경우에도 유효하지 않다.
ALTER TABLE entry
    ADD CONSTRAINT ck_entry_amount CHECK (amount BETWEEN 1 AND 999999999);
