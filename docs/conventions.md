# API · JPA · 테스트 규칙

## API

- 기본 경로 `/api/v1`, REST 리소스 중심:

```text
/api/v1/auth
/api/v1/users
/api/v1/groups
/api/v1/groups/{groupId}/members
/api/v1/groups/{groupId}/ledgers
/api/v1/ledgers/{ledgerId}/entries
/api/v1/entries/{entryId}/approval
/api/v1/groups/{groupId}/dashboard
/api/v1/groups/{groupId}/dues
/api/v1/groups/{groupId}/reports
```

- 성공 응답: 공통 래퍼 없이 HTTP 상태 코드 + 응답 DTO.
- 오류 응답: 공통 형식으로 통일, 오류 코드는 문자열 상수 관리. JPA/SQL/내부 오류를 그대로 노출 금지.
- 목록 API는 무한 스크롤 전제. 내역 목록은 `occurredDate + id` cursor pagination 우선.
- 중복 요청 위험이 큰 생성 API(내역 생성, 납부 확인)는 idempotency 고려.

## JPA

- `spring.jpa.open-in-view=false`, `ddl-auto=validate` (운영·개발 동일). 스키마 변경은 Flyway만.
- 연관관계 기본 LAZY. Controller에서 지연 로딩이 터지지 않도록 Service 트랜잭션 안에서 DTO 완성.
- 조회는 fetch join / EntityGraph / DTO projection. 목록 조회는 N+1 검토 필수.
- 양방향 연관관계는 실제 반대 방향 탐색이 필요할 때만. `CascadeType.ALL`·`orphanRemoval` 습관적 사용 금지(부모 생명주기에 완전 종속된 자식만).

## 보안

- 모든 모임 리소스: 리소스 ID 조회 후 바로 반환 금지 — 요청자 GroupMember 소속 + 역할 검증을 Service 계층에서 보장.
- 파일: 크기·Content-Type 검증, 원본 파일명을 storage key로 직접 사용 금지, 내역·파일의 모임 소유권 검증.

## 테스트

- Service 단위 테스트보다 **핵심 비즈니스 규칙 테스트** 우선.
- Repository 쿼리는 실제 MySQL 기반 Testcontainers로 검증. 주요 API는 RestAssured 통합 테스트.
- 필수 테스트 대상:
  - 모임 권한, 다른 모임 데이터 접근 차단
  - 내역 승인·반려, 승인 내역의 잔액 반영, 취소 내역의 잔액 제외
  - 중복 요청 방지
  - 비회원 모임원 ↔ 회원 계정 연결
  - 회비 분할 납부, 납부와 장부 수입의 중복 연결 방지
