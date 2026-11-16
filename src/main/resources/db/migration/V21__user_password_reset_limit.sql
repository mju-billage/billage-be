-- 비밀번호 재설정(임시 비밀번호 발송, 화면 COM-2-PAGE-02-0)의 발송 횟수 제한.
--
-- 이 API 는 로그인 없이 이메일만으로 부른다. 제한이 없으면 남의 주소를 반복해 넣는 것만으로
-- 그 사람의 비밀번호를 계속 바꿔 로그인을 막고 메일함을 채울 수 있다.
-- 이메일 인증(V17)과 같은 방식으로 창 안의 발송 횟수를 센다 — 창이 지나면 0 부터 다시 센다.
ALTER TABLE users
    ADD COLUMN password_reset_count INT NOT NULL DEFAULT 0 AFTER marketing_agreed_at,
    ADD COLUMN password_reset_window_started_at DATETIME(6) NULL AFTER password_reset_count;
