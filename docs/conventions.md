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

- 테스트는 **비즈니스 로직**(Service의 도메인 규칙·상태 전이·계산)과 **외부 연동**(Provider API 호출, Repository 쿼리 등) 위주로 작성한다. 토큰 절약을 위해 범위를 넓히지 않는다.
- API 응답 자체(상태 코드·JSON 필드 형태 등)를 검증하는 테스트는 새로 작성하지 않는다. 컨트롤러가 Service 결과를 그대로 응답에 매핑하는 경우 별도 테스트가 필요 없다 — Service 테스트로 충분.
- 코드를 읽으면 동작이 바로 보이는 부분(단순 위임, 단순 getter/매핑, 프레임워크가 보장하는 동작)은 테스트하지 않는다.
- Service 단위 테스트보다 **핵심 비즈니스 규칙 테스트** 우선.
- Repository 쿼리는 실제 MySQL 기반 Testcontainers로 검증.
- 모임 권한처럼 **다른 요청 경로로는 검증할 수 없는 보안/정합성 규칙**만 예외적으로 RestAssured 통합 테스트로 확인한다(단순 성공 응답 확인 목적의 API 테스트는 지양).
- 필수 테스트 대상:
  - 모임 권한, 다른 모임 데이터 접근 차단
  - 내역 승인·반려, 승인 내역의 잔액 반영, 취소 내역의 잔액 제외
  - 중복 요청 방지
  - 비회원 모임원 ↔ 회원 계정 연결
  - 회비 분할 납부, 납부와 장부 수입의 중복 연결 방지
