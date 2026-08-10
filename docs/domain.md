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
Group 1 ─ N Ledger                    (장부)
Ledger 1 ─ N Transaction              (내역은 장부 하나에 소속)
Transaction N ─ 0..1 GroupMember      (담당자, 선택)
Group 1 ─ N Fee N ─ 1 Ledger          (회비 항목이 장부 하나에 연결)
Fee 1 ─ N FeeMember N ─ 1 GroupMember (회비별 납부 대상)
Fee 0..1 ─ 1 Transaction              (마감 시 FEE_CLOSING 수입 내역 1건 생성·연결)
Transaction 1 ─ N Attachment          (증빙, 최대 10장)
Attachment 1 ─ N ReceiptItem          (영수증 OCR 품목)
Folder                                 ← 미확정. 내역 명세에 등장하지 않아 관계를 정하지 않음
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

### Folder (미확정 — 폴더 화면명세 확인 전까지 착수 금지)
- GNB에 '폴더' 탭이 따로 있으나, **내역 화면명세 어디에도 폴더가 등장하지 않는다**(필터·상세·목록 모두 장부 기준).
- 따라서 Transaction에 `folderId`를 두지 않는다. 폴더 명세를 확인한 뒤 관계를 정한다.

### Transaction
- 수입/지출 내역. 반드시 **장부(Ledger) 하나**에 소속. 금액 `BIGINT`(원화), **항상 양수**, `type` = INCOME | EXPENSE.
- 필드: `ledgerId`(필수), `title`(내역명, 필수), `amount`(필수), `type`, `occurredDate`(수입일/지출일),
  `assigneeId`(담당자, nullable), `memo`(nullable), `status`, `source`, `feeId`(nullable), `deletedAt`, `@Version`.
- **필수값은 금액·장부·내역명** 3개. 담당자·메모·증빙은 선택(상세 조회에서 미입력 항목은 `-`로 표기).
- 금액 상한 **999,999,999원**(9자리) — 초과 입력 차단.
- 승인 상태: **`PENDING / APPROVED` 2단계**. GENERAL 등록 → PENDING, OWNER 등록 → 즉시 APPROVED.
  반려(REJECTED) 상태는 두지 않는다 — 명세의 승인 요청 상세 화면에 반려 버튼·사유 입력이 없고, 거절은 삭제로 처리한다.
- 출처 `source`: `MANUAL`(직접) / `OCR`(영수증) / `FEE_CLOSING`(회비 마감 자동 생성).
- FEE_CLOSING 내역은 상세 흐름이 다르므로 원본 회비를 `feeId`로 연결하며, **삭제할 수 없다**(회비 마감에서만 해제).
- **APPROVED만** 잔액·통계·보고서에 반영(삭제된 내역 제외).

> `assigneeId`는 GroupMember(모임원 명단) 참조로 본다 — 명세의 담당자 값이 모임원 이름이기 때문.
> 다만 담당자 선택 화면을 아직 못 봤으므로, 모임원 명단 구현 시 재확인한다.

### Fee
- 회비 항목. `groupId, ledgerId, title, amountPerMember, targetAmount, dueDate, status, closedAt, transactionId`.
- 마감 금액은 실납부 합계가 아니라 **`targetAmount`(목표 금액)**. 상태 `OPEN / CLOSED`.

### FeeMember
- 회비별 납부 대상 모임원. `feeId, groupMemberId, paymentStatus(UNPAID/PAID), paidAt, memo`.
- 런칭은 미납/완납 **2단계만** — 분할·부분·초과 납부 자동 정산 없음.

### Attachment (증빙 자료)
- 증빙(영수증) 메타데이터만 DB에 저장 (storage key, 파일명, 타입, 크기). 바이너리는 Object Storage.
- **내역당 최대 10장**. 상세 화면은 `2/10` 형태로 개수를 표기한다.
- 일반 사진은 즉시 삭제. OCR 연동 영수증은 삭제 시 아래 ReceiptItem도 함께 삭제한다.

### ReceiptItem (증빙 자료 세부 내역, OCR)
- 영수증 OCR로 추출한 품목: `attachmentId, name(상품명), quantity(수량), amount(금액)` + 화면에서 합계 표시.
- OCR 영수증을 덮어쓰면 기존 품목 정보는 파기하고 새로 등록한다.
- OCR 등록 데이터가 없으면 상세 화면에서 해당 영역 자체를 숨긴다.

## 내역 승인 정책

- GENERAL 등록 → `PENDING`. OWNER 직접 등록 → `APPROVED`.
- 승인은 **OWNER만**. 승인 요청 상세 화면에서 OWNER가 내용을 **수정한 뒤 승인**할 수 있다(승인과 수정이 한 화면).
- **반려는 없다.** 명세에 반려 버튼·사유 입력이 없고, 거절은 내역 삭제로 처리한다.
- GENERAL은 자신이 등록한 `PENDING` 내역만 수정/삭제 가능.
- 내역 목록의 `승인요청` 탭에는 **GENERAL이 등록한 PENDING 내역만** 노출한다.
- 주요 엔티티(Transaction·Fee)에 `@Version` 낙관적 락.

## 내역 목록·조회 정책

- 목록은 **모임 단위**로 조회하고 장부는 필터 조건이다(장부별 URL이 아니라 `groupId` 기준 + `ledgerIds` 필터).
- 페이징: 최초 20건, 이후 스크롤 시 10건씩. `occurredDate + id` cursor pagination.
- 정렬: `최신순`(기본) / `과거순`. 두 방향 모두 cursor가 동작해야 한다.
- 필터는 **AND 결합**: 기간(1·3·6개월·직접입력, 기본값 전체) × 장부(다중 선택) × 구분(전체/수입/지출).
- 목록 응답에는 **총 건수**와 **기간 합계(수입·지출·합계)**가 함께 필요하다 — 상단 잔액 카드와 건수 표시가 필터에 실시간 연동된다.
- 검색은 `내역명` 또는 `장부명` 부분 일치, 검색어 최대 20자, 정렬은 최신순 고정.
- 상세 조회는 읽기 전용. 미입력 선택 항목(메모 등)은 빈 값이 아니라 `-`로 내려도 되도록 클라이언트와 합의된 상태.
- FEE_CLOSING 내역 상세는 별도 화면 — 납부자 명수·명단(이름, 납부 금액)과 원본 회비로 가는 링크가 필요하다.

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
| 내역(Transaction) | **앱에 복구 수단을 제공하지 않는다**("삭제한 내역은 다시 복구할 수 없어요"). DB는 감사 추적을 위해 `deletedAt` 소프트 삭제로 남기고 모든 조회·집계에서 제외한다. 복원 API는 만들지 않는다. |
| 내역의 증빙·OCR 품목 | 내역 삭제 시 함께 제외 처리. 삭제 후 해당 장부 잔액을 즉시 재계산해 응답한다. |
| FEE_CLOSING 내역 | 삭제 불가 — 마감된 회비에서만 해제된다. |
| 모임원(GroupMember) | 물리 삭제 대신 `active=false` 비활성화, 납부 이력 보존. |
| 내역 있는 장부 | 삭제 대신 보관(`ARCHIVED`) — OWNER만. |
| 회비(Fee) | 마감(CLOSED) 후 삭제 금지(생성된 내역 보존). 미마감 회비 삭제는 자동 수입 생성 없음. |
| 모임(Group) | 즉시 삭제 금지, 보관 상태 우선. |
| 탈퇴 사용자 | 유일 OWNER면 권한 위임(GroupManager) 완료 후에만 탈퇴 가능. |

내역 삭제는 사용자에게 **영구 삭제로 보이지만** 물리 삭제하지 않는다. 화면명세의 "복구할 수 없어요"는
앱에 복구 동선이 없다는 뜻으로 해석했고, 재무 데이터 물리 삭제 금지(CLAUDE.md 절대 규칙 2)를 함께 지키기 위한 결정이다.

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
