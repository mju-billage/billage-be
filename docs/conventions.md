# API · JPA · 테스트 규칙

## API

**API 스펙의 기준은 노션 API 명세**다(프론트 합의본). 구현 전 해당 도메인 페이지를 읽고, 구현이 달라지면 노션도 함께 고친다.
- 최상위: https://grand-addition-420.notion.site/API-3a5f756d01c881d1a1eed079f33d663c
- 공통 규칙 page id `3a3f756d01c8819e901acbee8505ae6c` / 도메인별 명세 DB `collection://551cd41d-394f-4374-9c51-73f8748f60a3`

- 기본 경로 `/api/v1`, REST 리소스 중심:

```text
/api/v1/auth
/api/v1/groups
/api/v1/groups/{groupId}/memberships     ← 관리자 권한
/api/v1/groups/{groupId}/members         ← 납부 명단
/api/v1/groups/{groupId}/invitations
/api/v1/groups/{groupId}/folders
/api/v1/folders/{folderId}/ledgers
/api/v1/ledgers/{ledgerId}/entries
/api/v1/entries/{entryId}/approve
/api/v1/groups/{groupId}/dashboard
/api/v1/groups/{groupId}/dues
/api/v1/groups/{groupId}/reports
```

- 인증된 사용자 본인 정보는 `/api/v1/auth/me`(조회·수정·탈퇴), 비밀번호 변경은 `/api/v1/auth/password`.
  (`/api/v1/users` 는 만들지 않았다 — 명세에 남아 있으면 정리할 것.) 로그인·토큰은 `AuthController`,
  본인 계정 관리는 `com.billage.user.UserController` 가 맡는다.
- 성공 응답: `com.billage.common.response.ApiResponse` 로 `{ "data": ..., "message": "..." }` 래핑(프론트 합의). 목록은 비어 있어도 `[]`.
  본문이 없는 삭제·탈퇴는 래퍼 없이 `204 No Content`.
- 오류 응답: 공통 형식(`{ code, message, fieldErrors }`)으로 통일 — **오류는 래핑하지 않는다**.
  경로 오타(404)·메서드 오기(405)·Content-Type 오기(415)·업로드 상한 초과(413) 같은 프레임워크 예외도
  `GlobalExceptionHandler` 에서 같은 형식으로 내린다 — catch-all 이 4xx 를 500 으로 삼키지 않도록 구체 핸들러를 유지할 것.
  오류 코드는 `ErrorCode` enum 으로 관리하고, 프론트는 `message` 가 아닌 `code` 로 분기한다. JPA/SQL/내부 오류를 그대로 노출 금지.
- 401 은 사유를 구분해 내린다 — `TOKEN_EXPIRED`(재발급 후 재시도) / `TOKEN_INVALID`(재로그인) / `UNAUTHORIZED`(토큰 없음).
  Refresh Token 계열(`REFRESH_TOKEN_*`)은 별도 코드이며, 이걸로는 재발급을 재시도하면 안 된다(무한 루프).
- 응답의 파일 URL(`fileUrl`·`groupImageUrl`)은 절대 주소로 완성해 내린다(`FileUrlResolver`).
  RN `<Image>` 가 상대경로를 해석하지 못한다. 주소는 `billage.file.public-base-url` 설정값이 우선이고, 비면 요청에서 유추한다.
  해당 경로는 인증이 필요하므로 클라이언트는 `Authorization` 헤더를 함께 실어야 한다.
- 날짜·시각은 ISO 8601. 시각은 오프셋 포함(`2026-07-20T18:00:00+09:00`) — `KoreanTime.toOffset` 사용. 날짜는 `2026-07-20`.
- 인증된 사용자 ID는 컨트롤러에서 `@CurrentUserId Long userId` 로 받는다.
- 목록 API는 노션 명세를 따른다 — 내역은 `page/size/sort` 기반 페이지네이션(`PageResponse`, 기본 20건·`occurredOn,id desc`), 모임원·관리자·폴더·장부 목록은 전체 반환.
- 중복 요청 위험이 큰 생성 API(내역 생성, 납부 확인)는 idempotency 고려.

## JPA

- `spring.jpa.open-in-view=false`, `ddl-auto=validate` (운영·개발 동일). 스키마 변경은 Flyway만.
- 연관관계 기본 LAZY. Controller에서 지연 로딩이 터지지 않도록 Service 트랜잭션 안에서 DTO 완성.
- 조회는 fetch join / EntityGraph / DTO projection. 목록 조회는 N+1 검토 필수.
- 양방향 연관관계는 실제 반대 방향 탐색이 필요할 때만. `CascadeType.ALL`·`orphanRemoval` 습관적 사용 금지(부모 생명주기에 완전 종속된 자식만).

## 보안

- 모든 모임 리소스: 리소스 ID 조회 후 바로 반환 금지 — 요청자 GroupMembership 소속 + 역할 검증을 Service 계층에서 보장(`GroupAccessGuard`).
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
  - 내역 승인, 승인 내역만 잔액·통계에 반영
  - 중복 요청 방지
  - 관리자 권한(GroupMembership)과 납부 명단(Member)의 분리 — 참여/탈퇴가 명단에 영향을 주지 않을 것
  - 납부와 장부 수입의 중복 연결 방지
