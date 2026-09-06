-- 마이페이지(내 프로필 · 비밀번호 변경 · 회원 탈퇴)와 가입 약관 동의.
--
-- 1) 프로필 이미지는 모임 대표 이미지(V13)와 같은 방식이다 — 파일 쪽에 주인을 적고 UNIQUE 로 하나만 허용한다.
-- 2) 탈퇴는 계정 행을 실제로 지운다. 그런데 users 를 참조하는 FK 세 곳이 남아 있어 그대로는 지울 수 없고,
--    이 참조들은 "누가 만들었나"를 적어 둔 기록일 뿐이라 사람이 사라져도 데이터 자체는 남아야 한다
--    (명세: "탈퇴해도 과거 회계 이력은 남습니다"). 그래서 NULL 을 허용하고 탈퇴 시 비운다.
--    내역(entry)은 작성자·승인자 이름을 그 시점 값으로 이미 스냅샷해 두어 손댈 것이 없다.
-- 3) 탈퇴 사유는 통계 목적이므로 계정과 이어 두지 않는다(명세: "개인 식별 정보와 분리해 저장").

-- 마케팅 수신 동의. 필수 3종은 동의해야만 가입이 되므로 users.terms_agreed_at 하나로 증명되지만,
-- 선택 항목인 마케팅은 동의 여부와 시각을 따로 남겨야 이후 수신 이력을 증명할 수 있다.
ALTER TABLE users
    ADD COLUMN marketing_agreed_at DATETIME(6) NULL AFTER terms_agreed_at;

-- 프로필 이미지. group_id 와 같은 자리이며 사용자당 하나(uk_file_user).
-- uploaded_by 는 탈퇴 시 비우기 위해 NULL 을 허용한다 — 증빙까지 함께 사라지면 남은 관리자의 회계 이력이 깨진다.
ALTER TABLE file
    ADD COLUMN user_id BIGINT NULL AFTER group_id,
    ADD CONSTRAINT uk_file_user UNIQUE (user_id),
    ADD CONSTRAINT fk_file_user FOREIGN KEY (user_id) REFERENCES users (id),
    MODIFY COLUMN uploaded_by BIGINT NULL;

ALTER TABLE group_space
    MODIFY COLUMN created_by BIGINT NULL;

ALTER TABLE group_invitation
    MODIFY COLUMN created_by BIGINT NULL;

-- 탈퇴 사유(통계용). user_id 를 두지 않는다.
CREATE TABLE withdrawal_reason (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    reason     VARCHAR(30) NOT NULL,
    detail     VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
