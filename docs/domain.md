# 도메인 정책

기준: **최종 화면명세서 + 글로벌 정책(ver 0.25)** 확정안. 충돌 시 최종 명세 우선. 로그인·회원가입·대시보드 일부 화면은 추후 수정 가능성 있음.
(이 문서는 최종 명세 반영으로 재작성됨 — 이전의 `GroupSpace / LedgerEntry / LedgerFolder / DuesRound·DuesCharge·DuesPayment` 및 3단계 역할·분할납부 설계는 폐기.)

## 핵심 개념: 사용자 ≠ 모임원

- **로그인 사용자(User)**, **모임 관리 권한(GroupManager)**, **모임원 명단(GroupMember)**은 서로 다른 개념이다.
- 초대 코드로 모임에 참여하면 관리자(`GroupManager`, 역할 `GENERAL`)가 될 뿐, **`GroupMember`로 자동 등록되지 않는다.**
- 총무가 `[모임원 추가]`로 별도 등록해야 모임원 명단·납부관리 명단에 표시된다.
- 따라서 `GroupMember`는 User와 독립적으로 존재하며 `userId`는 필수값이 아니다.

## 도메인 구조와 관계

```text
User 1 ─ N GroupManager N ─ 1 Group   (사용자↔모임 관리 권한, 권한 주체)
Group 1 ─ N GroupMember               (모임원 명단, User와 독립)
Group 1 ─ N Ledger 1 ─ N Folder       (장부/폴더, 폴더 중첩 없음)
Ledger 1 ─ N Transaction              (내역, 폴더는 선택 folderId nullable)
Group 1 ─ N Fee N ─ 1 Ledger          (회비 항목이 장부 하나에 연결)
Fee 1 ─ N FeeMember N ─ 1 GroupMember (회비별 납부 대상)
Fee 0..1 ─ 1 Transaction              (마감 시 FEE_CLOSING 수입 내역 1건 생성·연결)
Transaction 1 ─ N Attachment          (증빙, 후순위)
```

앱 GNB: 대시보드 / 내역 / 폴더 / 납부관리 / 더보기

## 엔티티

### User
- 서비스 가입 계정. 이메일/비밀번호(BCrypt) 또는 소셜 로그인(구글·카카오)으로 생성. 소셜 전용 계정은 비밀번호 없음. 도메인 작업에서 수정하지 않는다.

### SocialAccount
- User와 소셜 Provider 연결. 한 User가 구글·카카오 모두 연결 가능. `(provider, providerUserId)` 유일.

### Group
- 하나의 동아리/모임. 엔티티명 `Group`, **테이블명 `groups`**(예약어 회피), API는 `/groups`.
- 초대 코드 `inviteCode`(unique) 보유 — 단일 코드, 재발급 가능, 만료 없음(런칭 최소).
- 상태 `ACTIVE / ARCHIVED`, 즉시 물리 삭제 금지.

### GroupManager (권한 주체)
- 사용자의 모임 관리 권한. `(groupId, userId)` 유일.
- 역할: `OWNER`(모임 생성 총무) / `GENERAL`(초대 코드 참여 일반 관리자).
- **모든 모임 리소스 권한 검증은 GroupManager 기준**(GroupMember 아님).

### GroupMember (모임원 명단)
- 납부관리·모임원 명단 전용. `userId` **nullable**(선택 연결, 인증 경로엔 미사용).
- 삭제는 물리 삭제 대신 `active=false` 비활성화 — 회비 납부 이력 보존.

### Ledger
- 장부. 모임 아래 여러 개. 이름 수정/삭제는 OWNER만. 내역 있으면 삭제 대신 보관(`ARCHIVED`).

### Folder
- 장부 내 그룹핑(선택). **중첩 없음**. 이름 수정/삭제는 OWNER만.
- Transaction의 `folderId`는 nullable — 폴더 미지정 내역도 장부 전체 내역에서 조회 가능.

### Transaction
- 수입/지출 내역. 반드시 장부 하나에 소속. 금액 `BIGINT`(원화), **항상 양수**, `type` = INCOME | EXPENSE.
- 승인 상태: `PENDING / APPROVED / REJECTED`. GENERAL 등록 → PENDING, OWNER 등록 → 즉시 APPROVED.
- 출처 `source`: `MANUAL`(직접) / `OCR`(영수증) / `FEE_CLOSING`(회비 마감 자동 생성).
- FEE_CLOSING 내역은 상세 흐름이 다르므로 원본 회비를 `feeId`로 연결.
- 반려 사유 `rejectionReason` nullable, `deletedAt` nullable(소프트 삭제), `@Version`.
- **APPROVED만** 잔액·통계·보고서에 반영(삭제된 내역 제외).

### Fee
- 회비 항목. `groupId, ledgerId, title, amountPerMember, targetAmount, dueDate, status, closedAt, transactionId`.
- 마감 금액은 실납부 합계가 아니라 **`targetAmount`(목표 금액)**. 상태 `OPEN / CLOSED`.

### FeeMember
- 회비별 납부 대상 모임원. `feeId, groupMemberId, paymentStatus(UNPAID/PAID), paidAt, memo`.
- 런칭은 미납/완납 **2단계만** — 분할·부분·초과 납부 자동 정산 없음.

### Attachment (후순위)
- 증빙(영수증) 메타데이터만 DB에 저장 (storage key, 파일명, 타입, 크기). 바이너리는 Object Storage. OCR 결과 등록 시 연결.

## 내역 승인 정책

- GENERAL 등록 → `PENDING`. OWNER 직접 등록 → `APPROVED`.
- 승인/반려는 **OWNER만**. 반려 시 `rejectionReason` 기록.
- GENERAL은 자신이 등록한 `PENDING` 내역만 수정/삭제 가능.
- APPROVED 내역: 물리 삭제 금지 — 소프트 삭제(`deletedAt`)로 처리하며 복원 가능.
- 주요 엔티티(Transaction·Fee)에 `@Version` 낙관적 락.

## 잔액·보고서

- 잔액 필드 저장 금지. 항상 `승인된 수입 합계 - 승인된 지출 합계`로 계산(삭제 내역 제외).
- 대시보드·보고서 동일 집계 기준. 초기엔 SQL 집계 쿼리만 — 성능 문제가 실측되기 전 집계 테이블/Redis 금지.
- 보고서는 실시간 조회. 파일 저장은 초기 필수 아님.

## 납부 관리 (마지막에 구현)

- 실제 계좌 거래내역과 자동 연동하지 않는다. 모임원별 납부 여부·메모 중심으로 유연하게 관리.
- 부족/초과 입금, 한 사람이 여러 명분 입금, 분할 입금 등 **자동 정산하지 않는다.**
- 흐름: 회비 생성 → 장부 선택 → 납부 대상 모임원 선택 → 미납/완납 관리 → 전원 납부 → 총무 마감.
- **회비 마감은 단일 트랜잭션**으로: ① Fee `CLOSED`·`closedAt` ② `targetAmount`로 INCOME 내역 1건 생성(`source=FEE_CLOSING`, `APPROVED`) ③ Fee↔Transaction 연결 ④ 전체 내역·연결 장부에서 조회 가능. **중복 마감 방지 필수**(`status`·`transactionId` 가드 + `@Version`).
- 회비 삭제 시 납부완료자 금액을 자동 수입으로 생성하지 않는다 — 필요하면 총무가 직접 등록.

## 삭제 정책

| 대상 | 정책 |
| --- | --- |
| 내역(Transaction) | 소프트 삭제(`deletedAt`) + 복원(`restore`). 일반 조회 제외. APPROVED도 물리 삭제 금지. 복원 시 권한·복원 가능 여부 검증. |
| 모임원(GroupMember) | 물리 삭제 대신 `active=false` 비활성화, 납부 이력 보존. |
| 내역 있는 장부/폴더 | 삭제 대신 보관(`ARCHIVED`) — OWNER만. |
| 회비(Fee) | 마감(CLOSED) 후 삭제 금지(생성된 내역 보존). 미마감 회비 삭제는 자동 수입 생성 없음. |
| 모임(Group) | 즉시 삭제 금지, 보관 상태 우선. |
| 탈퇴 사용자 | 유일 OWNER면 권한 위임(GroupManager) 완료 후에만 탈퇴 가능. |

폴더 전체 백업/복원은 향후 확장 가능 — 소프트 삭제 구조가 이를 막지 않도록 유지.

## 인증

- Access Token = JWT / Refresh Token = DB 저장, 재발급 시 회전 + 이전 토큰 폐기, 로그아웃 시 폐기.
- 인증 기능을 다른 도메인 엔티티에 강하게 결합하지 않는다.

### 간편 로그인(구글·카카오)

- 클라이언트(RN 앱)가 각 Provider SDK로 로그인해 얻은 토큰(구글 ID Token, 카카오 Access Token)만 백엔드로 전달 —
  백엔드는 OAuth Authorization Code Flow를 직접 처리하지 않는다.
- `POST /api/v1/auth/social/login`: `(provider, providerUserId)`로 연결된 계정이 있으면 즉시 로그인.
  없으면(최초 로그인) 토큰만 저장하지 않고 이메일만 응답해 클라이언트가 약관 동의·이름 입력 화면으로 이동하게 한다.
- `POST /api/v1/auth/social/signup`: 약관 동의 + 이름을 받아 가입을 완료하고 즉시 로그인 처리.
  Provider 토큰을 재검증하며(상태를 서버에 보관하지 않음), 같은 이메일의 계정이 이미 있으면 새로 만들지 않고 거기에 연결한다
  (Provider가 이메일 소유를 검증했으므로 안전하다고 간주).
- 약관 동의는 버전 이력 없이 `User.termsAgreedAt` 시각만 기록(초기 단순화, 약관 개정 이력 추적 필요해지면 별도 테이블로 분리).
- 이메일/비밀번호 로그인은 당분간 함께 유지.
