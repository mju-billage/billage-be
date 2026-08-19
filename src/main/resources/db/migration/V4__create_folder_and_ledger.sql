-- 폴더(Folder) · 장부(Ledger).
-- 폴더는 장부를 묶는 계층 구조로 parent_folder_id 가 NULL 이면 최상위다.
-- 장부는 폴더에 속하지만, 소유권 검증과 목록 조회를 위해 group_id 를 함께 보관한다
-- (장부는 같은 모임 안에서만 폴더를 옮길 수 있으므로 group_id 는 변하지 않는다).
CREATE TABLE folder (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    group_id         BIGINT      NOT NULL,
    parent_folder_id BIGINT      NULL,
    name             VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_folder_group FOREIGN KEY (group_id) REFERENCES group_space (id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_folder_id) REFERENCES folder (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 모임의 폴더 트리 조회용
CREATE INDEX idx_folder_group ON folder (group_id, parent_folder_id);

-- folder_id 는 NULL 허용: 최상위 폴더가 삭제되면 그 안의 장부는 최상위 영역으로 올라온다(폴더 미소속).
CREATE TABLE ledger (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    folder_id  BIGINT      NULL,
    name       VARCHAR(20) NOT NULL,
    -- 월별이 아니라 장부 전체 기간에 적용되는 총예산. 미설정 가능.
    budget     BIGINT      NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_group FOREIGN KEY (group_id) REFERENCES group_space (id),
    CONSTRAINT fk_ledger_folder FOREIGN KEY (folder_id) REFERENCES folder (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 폴더별 장부 목록·장부 수 집계용
CREATE INDEX idx_ledger_folder ON ledger (folder_id, name);
-- 최상위 영역 장부 조회용
CREATE INDEX idx_ledger_group ON ledger (group_id, name);
