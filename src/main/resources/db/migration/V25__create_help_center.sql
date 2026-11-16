-- 고객지원(더보기 > 설정): FAQ·공지사항·약관·문의.
-- 백오피스가 없으므로 FAQ·공지·약관 내용은 이 마이그레이션처럼 Flyway 로만 넣고 고친다.
-- 내용을 바꿀 때는 이 파일을 고치지 말고 새 버전에서 UPDATE/INSERT 한다.

CREATE TABLE faq (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    question   VARCHAR(100) NOT NULL,
    -- 줄바꿈은 \n, 빈 줄은 \n\n. 앱이 그대로 보여 준다.
    answer     TEXT         NOT NULL,
    sort_order INT          NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(100) NOT NULL,
    body       TEXT         NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 공지 목록(최신순) 조회용
CREATE INDEX idx_notice_created ON notice (created_at, id);

-- 약관 전문. 종류별 한 행. 원문을 받기 전에는 행을 넣지 않는다(조회 시 TERMS_NOT_FOUND).
CREATE TABLE terms (
    term_type  VARCHAR(20) NOT NULL,
    body       MEDIUMTEXT  NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (term_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 1:1 문의. 회원이 탈퇴해도 문의는 남기고 작성자 연결만 끊는다.
CREATE TABLE inquiry (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NULL,
    email      VARCHAR(255)  NOT NULL,
    title      VARCHAR(50)   NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inquiry_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- FAQ 시드: 프론트 확인요청(2026-11-08) 6번의 새 시안 문구 4건.
-- 메뉴 이름은 디자이너가 확인 중이라 바뀌면 새 마이그레이션으로 고친다.
INSERT INTO faq (question, answer, sort_order) VALUES
('새로운 모임 관리자는 어떻게 초대하나요?',
 '새로운 멤버 초대는 [더보기 > 모임 관리자] 메뉴에서 할 수 있어요. 모임 관리자 화면 상단의 ’초대코드’를 확인하시어 코드를 복사한 뒤, 카카오톡 등 메신저로 간편하게 공유해 보세요!\n\n모임 초대코드를 알고 있는 빌리지 사용자는 [더보기 > 새 모임 추가하기] 메뉴를 통해 해당 모임에 코드로 참여할 수 있어요.',
 1),
('총무 권한을 다른 모임 관리자에게 넘길 수 있나요?',
 '네, 총무 권한을 다른 멤버에게 넘길 수 있어요. [더보기 > 모임 관리자]에서 특정 멤버의 프로필을 선택하고, 해당 멤버의 권한을 변경할 수 있어요.',
 2),
('모임의 내역을 초기화하고 새로 시작하려면 어떻게 하나요?',
 '모임의 기록을 새로 시작하고 싶다면 [폴더 > 메뉴 > 전체 백업]을 통해 기존 내역과 장부를 백업한 후 새로 시작할 수 있어요. 백업한 내용은 [더보기 > 보관함]에서 언제든지 확인할 수 있어요.',
 3),
('잘못 등록한 회비 내역이나 장부는 어떻게 삭제하나요?',
 '잘못 입력한 정보는 언제든 쉽게 지울 수 있어요.\n\n회비 내역: 해당 회비 상세 화면 우측 상단의 [더보기 > 삭제] 눌러주세요. 장부: 장부 상세 화면 우측 상단 메뉴에서 동일하게 삭제할 수 있어요.\n\n단, 한 번 삭제된 데이터는 다시 열람하거나 복구할 수 없으니 주의하세요!',
 4);
