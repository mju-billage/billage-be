-- 모임원 개별 추가·수정 화면(모임원 상세_수정)에 맞춰 선택 입력값을 추가한다.
-- 이름만 필수이며 전화번호·메모·태그는 모두 선택값이다(기획 확정 2026-08-23).
ALTER TABLE group_member
    ADD COLUMN phone_number VARCHAR(20) NULL AFTER name,
    ADD COLUMN memo         VARCHAR(30) NULL AFTER phone_number;

-- 모임원마다 자유 입력하는 태그. 모임 단위 태그 마스터는 두지 않는다(기획 확정 2026-08-23).
-- (member_id, name) 이 곧 식별자라 별도 PK 컬럼 없이 복합 기본키로 중복을 막는다.
CREATE TABLE member_tag (
    member_id BIGINT      NOT NULL,
    name      VARCHAR(10) NOT NULL,
    PRIMARY KEY (member_id, name),
    CONSTRAINT fk_member_tag_member FOREIGN KEY (member_id) REFERENCES group_member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
