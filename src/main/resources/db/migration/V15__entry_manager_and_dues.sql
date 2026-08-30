-- 내역의 담당자와 회비 참조.
--
-- 1) 담당자(manager) — 화면명세 「내역 추가」의 상세 정보 리스트는 "수입/지출일, 내역명, 담당자, 장부, 메모"다.
--    담당자는 이 지출·수입의 주체가 되는 모임 관리자이며, 기본값은 등록자 본인이지만 바꿀 수 있다.
--    작성자(created_by_user_id)와는 다르다 — 작성자는 "누가 입력했나"의 감사 기록이라 바뀌지 않고,
--    담당자는 "누구 건인가"라 등록 후에도 고칠 수 있다.
--
--    이름을 함께 저장하는 이유는 작성자·승인자와 같다. 그 사람이 모임을 나가거나 탈퇴해도
--    과거 회계 이력의 표시가 깨지면 안 된다.
ALTER TABLE entry
    ADD COLUMN manager_user_id BIGINT      NULL AFTER created_by_name,
    ADD COLUMN manager_name    VARCHAR(10) NULL AFTER manager_user_id;

-- 기존 내역은 담당자 개념 없이 등록됐다. 화면의 기본값 규칙(등록자 본인)을 그대로 적용해 채운다.
UPDATE entry
SET manager_user_id = created_by_user_id,
    manager_name    = created_by_name
WHERE manager_user_id IS NULL;

-- 2) 회비 참조 — 마감된 회비가 만든 수입 내역임을 표시한다.
--    화면은 이 값으로 목록에 납부관리 아이콘을 붙이고, 상세를 「상세 내역_납부관리_수입내역」으로 분기해
--    납부자 명단과 '회비 상세보기' 버튼을 보여 준다.
--
--    FK 를 걸지 않는다. 마감 즉시 회비와 내역은 서로 독립된 데이터가 되므로(기획 확정)
--    회비를 지워도 내역은 장부에 남아야 하고, 내역을 지워도 회비는 남아야 한다.
--    회비가 사라진 뒤에도 화면이 이름을 보여 줄 수 있도록 제목도 마감 시점 값으로 함께 스냅샷한다.
ALTER TABLE entry
    ADD COLUMN dues_id    BIGINT      NULL,
    ADD COLUMN dues_title VARCHAR(20) NULL;

-- 이미 마감된 회비가 만들어 둔 내역을 거꾸로 찾아 채운다.
UPDATE entry e
    JOIN dues d ON d.generated_entry_id = e.id
SET e.dues_id    = d.id,
    e.dues_title = d.title;

-- 내역 목록에서 회비 연결 여부를 함께 읽으므로 조회 조건에 자주 끼어든다.
CREATE INDEX idx_entry_dues ON entry (dues_id);
