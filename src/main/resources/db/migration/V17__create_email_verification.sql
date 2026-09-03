-- 회원가입 이메일 인증. 화면명세 COM-4-PAGE-01-0 — 6자리 숫자 코드, 3분 유효, 재전송 시 타이머 리셋.
--
-- 이메일당 한 행만 두고 발송할 때마다 덮어쓴다. 코드가 짧고 수명이 짧아도 평문으로 두지 않는다
-- (DB 를 읽을 수 있는 사람이 남의 가입을 가로챌 수 있다) — 비밀번호와 같은 인코더로 해시해 저장한다.
CREATE TABLE email_verification
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    email                  VARCHAR(255) NOT NULL,
    code_hash              VARCHAR(255) NOT NULL,
    expires_at             DATETIME(6)  NOT NULL,
    -- 6자리는 100만 가지뿐이라 시도를 제한하지 않으면 3분 안에도 무차별 대입이 가능하다.
    attempt_count          INT          NOT NULL DEFAULT 0,
    -- 발송 남용(메일 폭탄·SES 비용) 방지용. 창이 지나면 0 부터 다시 센다.
    send_count             INT          NOT NULL DEFAULT 0,
    send_window_started_at DATETIME(6)  NOT NULL,
    -- 인증이 끝난 시각. 가입 API 가 이 값으로 인증 여부를 판정한다.
    verified_at            DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
