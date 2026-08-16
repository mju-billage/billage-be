-- 업로드 파일 메타데이터. 바이너리는 DB가 아니라 저장소(현재 서버 디스크, 추후 Object Storage)에 둔다.
-- entry_id 는 증빙(RECEIPT)이 내역에 연결됐을 때만 값을 가진다. 연결된 파일은 삭제할 수 없다.
CREATE TABLE file (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    purpose            VARCHAR(20)  NOT NULL,
    storage_key        VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size               BIGINT       NOT NULL,
    uploaded_by        BIGINT       NOT NULL,
    entry_id           BIGINT       NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_file_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_file_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT fk_file_entry FOREIGN KEY (entry_id) REFERENCES entry (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 내역 상세에서 증빙 목록을 가져오기 위한 인덱스
CREATE INDEX idx_file_entry ON file (entry_id);
