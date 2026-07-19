---
name: new-feature
description: Billage 도메인 기능(엔티티/API/서비스) 신규 구현 또는 확장 시 따르는 체크리스트. 새 도메인, 새 엔드포인트, 새 엔티티 작업 시 사용.
---

# 새 기능 구현 워크플로

## 시작 전

1. `docs/domain.md`에서 해당 도메인 정책 확인 — 정책이 없거나 미확정이면 임의로 정하지 말고 사용자에게 확인.
2. `docs/conventions.md`의 API·JPA 규칙 확인.
3. 기존 유사 도메인 코드가 있으면 그 구조·네이밍을 그대로 따른다 (새 패턴 발명 금지).

## 구현 순서

1. **Flyway 마이그레이션** 먼저: `src/main/resources/db/migration/V{다음번호}__{설명}.sql`
   - 기존 마이그레이션 파일은 절대 수정 금지 (이미 적용된 버전).
   - FK, 필요한 인덱스(특히 목록 조회용 복합 인덱스)를 함께 정의.
2. **Entity**: `com.billage.{domain}` 아래. LAZY 기본, `@Version`(변경 경합 있는 엔티티), 상태는 enum. Setter 남발 금지 — 의미 있는 도메인 메서드로 상태 전이.
3. **Repository**: Spring Data JPA. 목록 조회는 N+1 검토, 필요 시 fetch join/projection.
4. **Service**: `@Transactional` 경계. **모임 소유권 + 역할 검증을 여기서 반드시 수행.** DTO를 트랜잭션 안에서 완성.
5. **Controller + DTO**: 엔티티 직접 반환 금지. Bean Validation 적용. 경로는 `docs/conventions.md`의 리소스 설계 준수.
6. **예외**: 공통 오류 형식 사용, 오류 코드 문자열 상수 추가.

## 완료 전 확인

- [ ] 다른 모임 멤버가 이 리소스에 접근하면 차단되는가? (통합 테스트로 검증)
- [ ] 재무 데이터라면: 승인 상태별 잔액 반영, 물리 삭제 대신 상태 전이인가?
- [ ] Repository 쿼리는 Testcontainers, 주요 API는 RestAssured 테스트 작성했는가?
- [ ] `./gradlew test` 통과 확인.

## 결과 보고 형식

변경 파일 / 구현 내용 / 테스트 방법 / 남은 이슈 순으로 정리.
