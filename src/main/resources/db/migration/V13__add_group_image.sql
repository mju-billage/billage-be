-- 모임 대표 이미지(화면: 더보기 > 모임 관리 > 모임 프로필 변경).
--
-- 증빙이 file.entry_id 로 주인을 적는 것과 같은 방식으로, 대표 이미지는 file.group_id 로 주인을 적는다.
-- 모임 쪽에 파일 ID 를 두는 반대 방향도 가능하지만, 그러면 "이 파일이 쓰이는가"를 두 테이블에 걸쳐
-- 확인해야 해서 잠금 없이는 경합을 막을 수 없다. 주인을 한 곳에만 적으면
-- UPDATE ... WHERE group_id IS NULL 한 줄로 선점이 원자적으로 끝난다.
--
-- UNIQUE (group_id) 로 모임당 대표 이미지는 하나. 증빙은 group_id 가 NULL 이고
-- MySQL 유니크 인덱스는 NULL 중복을 허용하므로 증빙 수에는 제약이 없다.
ALTER TABLE file
    ADD COLUMN group_id BIGINT NULL AFTER entry_id,
    ADD CONSTRAINT uk_file_group UNIQUE (group_id),
    ADD CONSTRAINT fk_file_group FOREIGN KEY (group_id) REFERENCES group_space (id);
