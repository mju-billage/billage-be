# Billage 백엔드

동아리·소모임 회비/수입/지출 관리 서비스의 Spring Boot REST API. React Native Android 앱이 클라이언트.
목표: 2026-08-31까지 개발 완료 후 Play Store 런칭. 확장성보다 빠른 개발·데이터 정합성·유지 가능한 구조 우선.

## API 명세의 기준

**노션 API 명세가 최종 기준이다.** `docs/`와 충돌하면 노션을 따르고 `docs/`를 고친다.
위치·페이지 ID는 `docs/conventions.md` 참고. MCP Notion 도구로 읽고, 구현이 명세와 달라지면 노션도 함께 수정한다.

## 상세 문서 (해당 작업 시 반드시 먼저 읽기)

- `docs/domain.md` — 엔티티·관계·상태·승인/납부/삭제 정책. **도메인 로직, 엔티티, 마이그레이션 작업 전 필독**
- `docs/conventions.md` — API·JPA·테스트 규칙. **컨트롤러/리포지토리/테스트 작성 전 필독**
- `docs/infra.md` — 배포·환경·개발 우선순위·제외 범위

## 스택

Java 21 · Spring Boot 4.1.0 · Gradle Groovy · Spring MVC/Data JPA/Security · MySQL 8.4 LTS · Flyway · springdoc-openapi 3.x · JUnit 5 + RestAssured + Testcontainers · Lombok

주의: Boot 4라서 스타터명이 `spring-boot-starter-webmvc`, 테스트도 모듈별 스타터. Testcontainers(BOM 1.21.4)·RestAssured(5.5.7)는 버전 명시 필수 — 2.x/6.x 메이저로 올리지 말 것.

## 아키텍처

모듈형 모놀리스, 기능 중심 패키지: `com.billage` 아래
`common / auth / user / group / membership / member / folder / ledger / entry / dues / report / file`

**`membership` ≠ `member`**: `membership`(GroupMembership)은 가입 사용자의 모임 관리자 권한(OWNER/MEMBER),
`member`(Member)는 회비 납부 대상 명단(이름만, 권한 없음). 둘은 자동 연결하지 않는다.

각 도메인은 Controller → Service → Repository + Entity/DTO 단순 계층. 인터페이스 분리는 외부 저장소나 복잡한 정책이 있을 때만. 엔티티를 Controller 응답으로 직접 반환 금지.

## 절대 규칙 (모든 작업에 적용)

1. **소유권 검증**: 모든 모임 리소스 접근 시 리소스 ID 조회만으로 반환하지 말고, 요청자의 GroupMembership 소속·역할(OWNER=총무 / MEMBER=일반 관리자)을 Service 계층에서 반드시 확인. `com.billage.membership.GroupAccessGuard` 사용. 납부 명단(Member)은 권한 판정에 쓰지 않는다.
2. **재무 데이터 처리**: 잔액은 저장하지 않고 `승인된 수입 합계 - 승인된 지출 합계`로 계산. 삭제는 노션 명세대로 **Hard Delete**(모임·관리자·명단·장부·내역·회비). 승인 상태와 무관하게 완전 삭제하며 별도 이력을 남기지 않는다.
3. **스키마는 Flyway로만** 변경. `ddl-auto=validate` 고정.
4. `open-in-view=false`, 연관관계 기본 LAZY, DTO는 Service 트랜잭션 안에서 완성.
5. 금액은 원화 `BIGINT`, 항상 양수, `type`(INCOME/EXPENSE)으로 구분.
6. 미확정 정책(납부 관리 등)을 임의로 단정하지 말고 격리해 두거나 사용자에게 확인.
7. 새 라이브러리 추가 전 기존 스택으로 해결 가능한지 확인. 비밀값 커밋 금지.

## 작업 결과 보고

변경 파일, 구현 내용, 테스트 방법, 남은 이슈를 함께 정리한다.
