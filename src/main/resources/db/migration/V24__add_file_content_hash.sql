-- 업로드한 바이트의 SHA-256(소문자 hex 64자). 같은 내역에 같은 사진이 두 번 붙는 것을 막는 데 쓴다.
-- 이 마이그레이션 이전에 올린 파일은 값을 알 수 없어 NULL 이며 중복 검사 대상에서 빠진다(백필하지 않음).
ALTER TABLE file
    ADD COLUMN content_hash VARCHAR(64) NULL AFTER size;

-- 내역 단위 중복 검사용
CREATE INDEX idx_file_entry_hash ON file (entry_id, content_hash);
