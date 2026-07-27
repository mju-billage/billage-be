# 인프라 · 개발 순서 · 제외 범위

## 인프라

- dev / prod 서버 분리 (개발 중엔 EC2 공용 dev 서버 1대). DB도 분리, 공유 금지.
- Caddy = HTTPS + reverse proxy. Spring Boot는 systemd 실행.
- GitHub Actions: develop push → dev 배포 / release tag 또는 수동 → prod 배포.
- 파일은 S3 호환 Object Storage. DB는 주기적 덤프 → S3 백업.
- 초기에 RDS·ALB·Redis·Kafka 사용 안 함. 비밀값은 Git에 커밋 금지.
- 로컬: 앱은 IDE 실행, MySQL만 Docker Compose.

### dev 서버 구축 현황 (구현됨 — 상세·명령은 `deploy/README.md`)

- **구조**: RN 앱 →HTTPS→ Caddy(443) →reverse_proxy→ Spring Boot(systemd, 127.0.0.1:8080) → MySQL 8.4(Docker, 127.0.0.1:3306). 단일 EC2 t3.micro(Ubuntu 24.04, RAM 1GB, 스왑 2G), ap-northeast-2.
- **주소**: 탄력적 IP `52.78.148.114` 고정 → `https://52-78-148-114.nip.io` (Caddy 자동 Let's Encrypt). nip.io는 실도메인 생기면 `caddy.env`의 SITE_ADDRESS만 교체.
- **배포**: `develop` push → Actions `deploy-dev.yml`(bootJar→scp→restart). 헬스체크 실패 시 **이전 jar 자동 롤백**. 시크릿 `EC2_HOST/EC2_USER/EC2_SSH_KEY`.
- **백업**: 매일 03:30 KST mysqldump→gzip(로컬 14일) + S3 `billage-db-backup-442908904609`(SSE-S3, 30일). S3 인증은 EC2 인스턴스 역할(서버에 키 없음). 복구 `deploy/restore-db.sh`.
- **하드닝**: 앱·MySQL 외부 미노출(localhost 바인딩+보안그룹 22/80/443만), JVM Xmx384m·MySQL mem_limit 360m·performance_schema OFF, journald 200M 상한, 자가점검 cron(`healthcheck.sh`) 5분.
- **미완**: prod 환경, UptimeRobot 외부 알림 등록. **주의: 현재 `SecurityConfig` 가 `permitAll()` 이라 전 엔드포인트 공개(접근 제어 없음)** — auth 도메인 구현 시 적용.

## 개발 우선순위

1. 프로젝트 공통 설정 → 2. 예외 처리·응답 규칙 → 3. Flyway·MySQL → 4. 인증·사용자 → 5. 모임·모임원 → 6. 권한 처리 → 7. 장부 폴더 → 8. 수입·지출 내역 → 9. 승인·반려 → 10. 증빙 파일 → 11. 대시보드·통계 → 12. 예산·보고서 → 13. 납부 관리 → 14. 프론트 연동 QA → 15. 런칭 서버 배포

## 초기 범위 제외 (만들지 말 것)

마이크로서비스 · Kafka · Redis 캐시 · CQRS · 이벤트 소싱 · 중첩 폴더 · 커스텀 권한 · 실시간 은행 계좌 연동 · 자동 입금 내역 수집 · OCR 자동 장부 등록 · 자체 푸시 알림 서버 · 불필요한 디자인 패턴과 과도한 추상화
