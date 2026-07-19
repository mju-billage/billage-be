# 도메인 정책

기획 자료 우선순위: ① 2026-07-19 최신 `화면명세서.zip` ② 이후 정책 문서·개발 확정안 ③ 이전 `Billage_IA v0.4`. 충돌 시 최신 우선. 로그인·회원가입·대시보드 일부 화면은 추후 수정 가능성 있음.

## 도메인 구조와 관계

```text
모임(GroupSpace) 1 ─ N 모임원(GroupMember)
모임 1 ─ N 장부 폴더(LedgerFolder)
장부 폴더 1 ─ N 수입·지출 내역(LedgerEntry)
모임 1 ─ N 회비 회차(DuesRound)
DuesRound 1 ─ N DuesCharge (모임원별 청구)
DuesCharge 1 ─ N DuesPayment (실제 납부 기록)
DuesPayment 0..1 ─ 1 LedgerEntry (납부 확인 → 장부 수입 연결)
```

앱 GNB: 대시보드 / 내역 / 폴더 / 납부관리 / 더보기

## 엔티티

### User
- 서비스 가입 계정. 이메일 로그인부터 구현. 비밀번호 BCrypt.

### GroupSpace
- 하나의 동아리/모임. `Group`은 Java/SQL 충돌 위험 → 엔티티명 `GroupSpace`, API는 `/groups`.

### GroupMember
- 모임 소속 인원. **`userId` nullable** — 비회원도 총무가 이름만으로 먼저 등록 가능.
- 비회원이 나중에 가입하면 기존 GroupMember에 계정을 연결 (납부·내역 이력이 끊기지 않음).
- 회비·납부 이력은 User가 아니라 **GroupMember 기준**으로 연결.
- 역할: `OWNER`(모임장) / `TREASURER`(총무) / `MEMBER`(일반). 커스텀 권한 없음.

### LedgerFolder
- 화면의 "폴더" = 백엔드의 장부 단위. 모임 아래 여러 개. **중첩 폴더 미지원**.
- 내역이 있으면 삭제 불가 → 보관(ARCHIVED) 처리.

### LedgerEntry
- 수입/지출 내역. 금액 `BIGINT`(원화), **항상 양수**, `type` = INCOME | EXPENSE.
- 상태: `DRAFT → PENDING → APPROVED | REJECTED`, 승인 후 취소는 `CANCELED`.
- **APPROVED만** 잔액·통계·보고서에 반영.

### EvidenceFile
- 증빙(영수증) 메타데이터만 DB에 저장 (storage key, 파일명, 타입, 크기). 바이너리는 Object Storage.

## 내역 승인 정책

- 일반 모임원 등록 → `PENDING`. 총무/모임장 직접 등록 → `APPROVED` 처리 가능.
- 승인 전 내역: 수정·물리 삭제 가능.
- 승인된 내역: 직접 덮어쓰기 금지. 수정은 변경 요청 또는 새 버전 생성 방식. 삭제는 `CANCELED` 처리.
- 승인·반려·수정·취소 기록은 추적 가능해야 함.
- 주요 엔티티에 `@Version` 낙관적 락.

## 잔액·보고서

- 잔액 필드 저장 금지. 항상 `승인된 수입 합계 - 승인된 지출 합계`로 계산.
- 대시보드·보고서 동일 집계 기준. 초기엔 SQL 집계 쿼리만 — 성능 문제가 실측되기 전 집계 테이블/Redis 금지.
- 보고서는 실시간 조회. 파일 저장은 초기 필수 아님.

## 납부 관리 (마지막에 구현, 정책 일부 미확정)

- 미확정 정책에 종속되는 코드를 먼저 작성하지 않는다.
- 청구(DuesCharge)와 납부(DuesPayment) 분리 → 분할 납부 지원.
- 납부 상태는 저장값보다 **확인된 납부 금액 합계로 계산**: `UNPAID / PARTIAL / PAID / OVERDUE / EXEMPT`.
- 납부 확인 시 장부 INCOME 내역 생성 또는 기존 내역 연결. **중복 납부 확인·중복 장부 반영 방지 필수**.

## 삭제 정책

| 대상 | 정책 |
| --- | --- |
| 승인 전 내역 | 물리 삭제 가능 |
| 승인된 내역 | `CANCELED` 처리 |
| 내역 있는 폴더 | 삭제 불가, 보관 처리 |
| 탈퇴 모임원 | `WITHDRAWN` 상태 보존, 납부·내역 기록 유지 |
| 모임 | 즉시 삭제 금지, 보관 상태 우선 |

## 인증

- Access Token = JWT / Refresh Token = DB 저장, 재발급 시 회전 + 이전 토큰 폐기, 로그아웃 시 폐기.
- 초기엔 이메일 회원가입·로그인만. 소셜 로그인은 정책 확정 후 Provider 추가.
- 인증 기능을 다른 도메인 엔티티에 강하게 결합하지 않는다.
