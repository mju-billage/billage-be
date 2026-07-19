---
name: db-migration
description: Flyway 마이그레이션 작성·수정 규칙. 테이블 생성/변경, 스키마 작업, DDL 관련 작업 시 사용.
---

# Flyway 마이그레이션 규칙

- 위치: `src/main/resources/db/migration/`, 파일명 `V{번호}__{snake_case_설명}.sql` (언더스코어 2개).
- 다음 번호는 디렉토리의 기존 최대 버전 + 1. **이미 존재하는 마이그레이션 파일은 절대 수정 금지** — 변경이 필요하면 새 버전 추가.
- `ddl-auto=validate`이므로 엔티티와 스키마가 정확히 일치해야 함. 마이그레이션 작성 후 엔티티 매핑과 대조.

## 스키마 컨벤션

- 테이블·컬럼: snake_case. `Group` 예약어 회피 → 모임 테이블은 `group_space`.
- PK: `id BIGINT AUTO_INCREMENT`.
- 금액: `BIGINT NOT NULL` (원화, 양수만).
- 상태: `VARCHAR` + 엔티티 enum (`@Enumerated(EnumType.STRING)`).
- 시각: `created_at`, `updated_at` 등 `DATETIME(6)`. 발생일은 `DATE`.
- FK는 명시적으로 선언. 목록 조회 패턴에 맞는 복합 인덱스 함께 추가 (예: 내역 목록 `(ledger_folder_id, occurred_date, id)`).
- 낙관적 락 대상 테이블에 `version BIGINT NOT NULL DEFAULT 0`.

## 검증

- 로컬 Docker MySQL(8.4)에서 앱 기동으로 validate 통과 확인, 또는 Testcontainers 테스트 실행.
- 파괴적 변경(컬럼 삭제, 타입 변경)은 dev DB에 적용되기 전에 사용자에게 확인.
