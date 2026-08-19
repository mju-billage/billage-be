-- 사용자 이름은 기획 글로벌 정책상 최대 10자인데 V1 에서 VARCHAR(100) 으로 만들어져 있었다.
-- 회계 이력 스냅샷(entry.created_by_name / approved_by_name)이 VARCHAR(10) 이라,
-- 10자를 넘는 이름의 사용자가 내역을 등록·승인하면 저장이 깨진다.
--
-- 정책을 넘긴 기존 이름은 잘라낸 뒤 컬럼을 정책에 맞춘다.
-- (10자 초과 이름은 애초에 정책 위반 상태로 들어온 값이라 잘라도 잃는 정보가 없다.)
UPDATE users SET name = LEFT(name, 10) WHERE CHAR_LENGTH(name) > 10;

ALTER TABLE users MODIFY COLUMN name VARCHAR(10) NOT NULL;
