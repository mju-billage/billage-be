-- 모임 대표 이미지(화면: 더보기 > 모임 관리 > 모임 프로필 변경).
--
-- file 테이블로 FK 를 걸지 않는다. 이미지 교체·모임 삭제 같은 파괴적 경로에서
-- "먼저 참조를 끊고 파일을 지운다"는 순서를 DB 제약이 강제하면 삭제 순서 함정이 생긴다.
-- 참조 정리는 GroupService 가 한 트랜잭션에서 책임진다.
-- 한 파일을 두 모임이 나눠 쓰면 한쪽에서 교체·삭제할 때 다른 쪽 참조가 깨진다.
-- MySQL 유니크 인덱스는 NULL 중복을 허용하므로 "이미지 없음"은 얼마든지 있을 수 있다.
ALTER TABLE group_space
    ADD COLUMN group_image_file_id BIGINT NULL AFTER description,
    ADD CONSTRAINT uk_group_space_image UNIQUE (group_image_file_id);
