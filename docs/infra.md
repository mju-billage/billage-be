# 인프라 · 개발 순서 · 제외 범위

## 인프라

- dev / prod 서버 분리 (개발 중엔 EC2 공용 dev 서버 1대). DB도 분리, 공유 금지.
- Caddy = HTTPS + reverse proxy. Spring Boot는 systemd 실행.
- GitHub Actions: develop push → dev 배포 / release tag 또는 수동 → prod 배포.
- 파일은 S3 호환 Object Storage. DB는 주기적 덤프 → S3 백업.
- 초기에 RDS·ALB·Redis·Kafka 사용 안 함. 비밀값은 Git에 커밋 금지.
- 로컬: 앱은 IDE 실행, MySQL만 Docker Compose.

## 개발 우선순위

1. 프로젝트 공통 설정 → 2. 예외 처리·응답 규칙 → 3. Flyway·MySQL → 4. 인증·사용자 → 5. 모임·모임원 → 6. 권한 처리 → 7. 장부 폴더 → 8. 수입·지출 내역 → 9. 승인·반려 → 10. 증빙 파일 → 11. 대시보드·통계 → 12. 예산·보고서 → 13. 납부 관리 → 14. 프론트 연동 QA → 15. 런칭 서버 배포

## 초기 범위 제외 (만들지 말 것)

마이크로서비스 · Kafka · Redis 캐시 · CQRS · 이벤트 소싱 · 중첩 폴더 · 커스텀 권한 · 실시간 은행 계좌 연동 · 자동 입금 내역 수집 · OCR 자동 장부 등록 · 자체 푸시 알림 서버 · 불필요한 디자인 패턴과 과도한 추상화
