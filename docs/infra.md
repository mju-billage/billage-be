# 인프라 · 개발 순서 · 제외 범위

## 인프라

- dev / prod 서버 분리 (개발 중엔 EC2 공용 dev 서버 1대). DB도 분리, 공유 금지.
- Caddy = HTTPS + reverse proxy. Spring Boot는 systemd 실행.
- GitHub Actions: develop push → dev 배포 / release tag 또는 수동 → prod 배포.
- 파일은 S3(`billage-files-442908904609`, `ap-northeast-2`). 앱 설정 `billage.file.storage=S3`, dev·prod 는 `prefix` 로 구분. 로컬 개발은 `LOCAL`(디스크). DB는 주기적 덤프 → S3 백업.
- 초기에 RDS·ALB·Redis·Kafka 사용 안 함. 비밀값은 Git에 커밋 금지.
- 로컬: 앱은 IDE 실행, MySQL만 Docker Compose.

### dev 서버 구축 현황 (구현됨 — 상세·명령은 `deploy/README.md`)

- **구조**: RN 앱 →HTTPS→ Caddy(443) →reverse_proxy→ Spring Boot(systemd, 127.0.0.1:8080) → MySQL 8.4(Docker, 127.0.0.1:3306). 단일 EC2 t3.micro(Ubuntu 24.04, RAM 1GB, 스왑 2G), ap-northeast-2.
- **주소**: 탄력적 IP `52.78.148.114` 고정 → `https://52-78-148-114.nip.io` (Caddy 자동 Let's Encrypt). nip.io는 실도메인 생기면 `caddy.env`의 SITE_ADDRESS만 교체.
- **배포**: `develop` push → Actions `deploy-dev.yml`(bootJar→scp→restart). 헬스체크 실패 시 **이전 jar 자동 롤백**. 시크릿 `EC2_HOST/EC2_USER/EC2_SSH_KEY`.
- **백업**: 매일 03:30 KST mysqldump→gzip(로컬 14일) + S3 `billage-db-backup-442908904609`(SSE-S3, 30일). S3 인증은 EC2 인스턴스 역할(서버에 키 없음). 복구 `deploy/restore-db.sh`.
- **하드닝**: 앱·MySQL 외부 미노출(localhost 바인딩+보안그룹 22/80/443만), JVM Xmx384m·MySQL mem_limit 360m·performance_schema OFF, journald 200M 상한, 자가점검 cron(`healthcheck.sh`) 5분.
- **미완**: prod 환경, UptimeRobot 외부 알림 등록. (접근 제어는 auth 도메인 구현으로 해결 — `SecurityConfig` 가 `anyRequest().authenticated()`, 로그인·재발급 계열만 공개.)

## 개발 우선순위

~~1. 프로젝트 공통 설정 → 2. 예외 처리·응답 규칙 → 3. Flyway·MySQL → 4. 인증·사용자 → 5. 모임·관리자·모임원 명단 → 6. 권한 처리 → 7. 폴더 → 8. 장부(예산 포함) → 9. 수입·지출 내역 + 승인 + 수정·삭제~~ **(완료, 수정·삭제는 2026-08-17 총무 전용 확정 후 구현)**
→ ~~10. 증빙 파일~~ **(완료)** → ~~11. 대시보드·통계~~ **(완료, 2026-08-16 — 회비 집계는 Dues 이후)** → ~~12. 보고서~~ **(완료, 2026-08-17 — 권한 확정 반영)** → ~~13. 납부 관리~~ **(완료, 2026-08-19 — 마감 취소 API 유무만 확인 대기)** → 14. 프론트 연동 QA → 15. 런칭 서버 배포

### 명세에 없는 미착수 기능 (2026-08-17 기획 문서에서 발견, 범위 확인 필요)

기획 0720 답변은 "화면명세서와 IA에 명시된 모든 기능을 구현하여 런칭"이라고 못박았는데, 다음은 **노션 API 명세에도 이 저장소에도 없다.**

- **폴더 전체 백업 / 기록 보관(보관함)** — "보관함에 저장되며 기존 폴더 및 장부는 전부 초기화". 회계 기간 마감·이월에 해당하는 독립 도메인. 파괴적 동작이라 설계 비용이 크다.
- **회비 요청** — 일반 권한 제한 목록에 "회비 요청 금지"로 등장. 알림성 기능이면 아래 제외 범위(자체 푸시)와 충돌 가능.
- **모임원 태그** — "모임원 추가 및 태그 적용". 현재 `Member` 는 이름만 가진다.
- **모임 프로필(이미지) 변경** — 화면에 존재. `groupImageFileId` 는 File 도메인 완료 후 되살릴 수 있다.

## 초기 범위 제외 (만들지 말 것)

마이크로서비스 · Kafka · Redis 캐시 · CQRS · 이벤트 소싱 · 커스텀 권한 · 실시간 은행 계좌 연동 · 자동 입금 내역 수집 · OCR 자동 장부 등록 · 자체 푸시 알림 서버 · 불필요한 디자인 패턴과 과도한 추상화
