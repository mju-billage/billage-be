-- 모임원 개별 추가·수정 화면(모임원 상세_수정)에 맞춰 선택 입력값을 추가한다.
-- 이름만 필수이며 전화번호·메모·태그는 모두 선택값이다(기획 확정 2026-08-23).
ALTER TABLE group_member
    ADD COLUMN phone_number VARCHAR(20) NULL AFTER name,
    ADD COLUMN memo         VARCHAR(30) NULL AFTER phone_number;

-- 모임원마다 자유 입력하는 태그. 모임 단위 태그 마스터는 두지 않는다(기획 확정 2026-08-23).
-- (member_id, name) 이 곧 식별자라 별도 PK 컬럼 없이 복합 기본키로 중복을 막는다.
--
-- collation 을 as_cs(악센트·대소문자 구분)로 못박는다. MySQL 8.4 기본값인 utf8mb4_0900_ai_ci 는
-- 대소문자를 구분하지 않아 "VIP" 와 "vip" 가 애플리케이션에서는 서로 다른 태그인데 DB 에서는
-- 같은 키가 되고, 둘을 같이 보내면 duplicate key 로 요청 전체가 500 이 된다.
CREATE TABLE member_tag (
    member_id BIGINT      NOT NULL,
    name      VARCHAR(10) COLLATE utf8mb4_0900_as_cs NOT NULL,
    PRIMARY KEY (member_id, name),
    CONSTRAINT fk_member_tag_member FOREIGN KEY (member_id) REFERENCES group_member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
