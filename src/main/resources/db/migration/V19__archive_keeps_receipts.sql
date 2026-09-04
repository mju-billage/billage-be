-- 보관은 "치우는 것"이 아니라 "남기는 것"이다. 그런데 V18 의 보관은 원본 내역을 지우면서
-- 증빙 이미지까지 함께 지웠다 — 되돌릴 수 없는 손실이라 증빙의 주인을 보관 기록으로 옮긴다.
--
-- file.entry_id 가 주인을 적는 자리였듯, archive_entry_id 가 보관된 내역을 가리킨다.
-- 둘 중 하나만 채워진다(현재 장부의 내역이거나, 보관된 기록이거나).
ALTER TABLE file
    ADD COLUMN archive_entry_id BIGINT NULL AFTER entry_id,
    ADD CONSTRAINT fk_file_archive_entry
        FOREIGN KEY (archive_entry_id) REFERENCES archive_entry (id);

CREATE INDEX idx_file_archive_entry ON file (archive_entry_id);
