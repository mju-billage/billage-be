# Billage dev 서버 배포 가이드

최소 사양(AWS 프리티어) 기준. 구조:

```
[RN Android 앱]  ──HTTPS──▶  Caddy(443) ──▶  Spring Boot(systemd, :8080) ──▶  MySQL(Docker, 127.0.0.1:3306)
                              무료 인증서            EC2 t3.micro                       localhost only
```

- **호스팅**: EC2 t3.micro (프리티어 1년 무료)
- **HTTPS/도메인**: Caddy 자동 인증서 + `nip.io` 무료 도메인 (도메인 생기면 1줄 교체)
- **배포**: `develop` push → GitHub Actions 가 jar 빌드 → EC2 로 전송 → systemd 재시작
- **DB**: MySQL 8.4 Docker 컨테이너, 외부 미노출. 백업은 이후 S3 덤프로 추가

> RDS/ALB/Redis/Kafka 안 씀 (infra.md 준수). 파일 S3 는 file 도메인 구현 시점(우선순위 10)에 붙임.

---

## 1. EC2 인스턴스 생성

AWS 콘솔 → EC2 → **인스턴스 시작**

| 항목 | 값 |
|------|-----|
| AMI | Ubuntu Server 24.04 LTS |
| 인스턴스 유형 | **t3.micro** (프리티어) |
| 키 페어 | 새로 생성 → `.pem` 다운로드 (배포 SSH 키로 재사용) |
| 스토리지 | 30GB gp3 (프리티어 한도) |
| 리전 | ap-northeast-2 (서울) 권장 |

**보안 그룹 인바운드** — 딱 3개만 연다:

| 포트 | 소스 | 용도 |
|------|------|------|
| 22 (SSH) | 내 IP | 서버 접속 |
| 80 (HTTP) | 0.0.0.0/0 | Caddy 인증서 발급 + HTTPS 리다이렉트 |
| 443 (HTTPS) | 0.0.0.0/0 | 앱 → API |

> 8080, 3306 은 **열지 않는다** (각각 localhost 전용).

퍼블릭 IP 를 메모해 둔다. 재부팅 시 IP 가 바뀌므로, 원하면 **탄력적 IP(Elastic IP)** 를 할당해 고정 (실행 중 인스턴스에 연결돼 있으면 무료).

---

## 2. 서버 최초 세팅 (1회)

로컬에서 `deploy/` 를 서버로 보내고 부트스트랩 실행:

```bash
# 로컬
scp -i billage.pem -r deploy ubuntu@52.79.226.129:/tmp/

# 서버
ssh -i billage.pem ubuntu@52.79.226.129
sudo bash /tmp/deploy/bootstrap.sh
```

부트스트랩이 스왑·JRE21·Docker·Caddy 설치와 파일 배치까지 해준다. 이후 안내대로 비밀값을 채운다:

```bash
sudo nano /etc/billage/mysql.env     # MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD
sudo nano /etc/billage/billage.env   # DB_PASSWORD = MYSQL_PASSWORD 와 동일하게
sudo nano /etc/caddy/caddy.env       # SITE_ADDRESS = <IP의 .을 -로>.nip.io  예) 52-79-226-129.nip.io

# MySQL 기동
cd /opt/billage && sudo docker compose --env-file /etc/billage/mysql.env up -d

# Caddy 반영 · billage 자동시작 등록
sudo systemctl restart caddy
sudo systemctl enable billage
```

---

## 3. GitHub Actions 시크릿 등록

레포 → Settings → Secrets and variables → Actions → **New repository secret**

| 이름 | 값 |
|------|-----|
| `EC2_HOST` | EC2 퍼블릭 IP (또는 탄력적 IP) |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | `billage.pem` **파일 전체 내용** (`-----BEGIN ...` 포함) |

---

## 4. 배포 실행

`develop` 브랜치에 push 하면 자동 배포된다.

```bash
git checkout develop
git push origin develop
```

또는 Actions 탭 → **Deploy to Dev** → Run workflow (수동).

워크플로가 하는 일: jar 빌드 → `/opt/billage/billage.jar` 로 전송 → `systemctl restart billage` → `/actuator/health` 로 기동 확인.

---

## 5. 확인

```bash
curl https://<SITE_ADDRESS>/actuator/health
# {"status":"UP"}
```

RN 앱의 API 베이스 URL 을 `https://<SITE_ADDRESS>` 로 설정하면 연결 끝.
(평문 HTTP 가 아니라 HTTPS 이므로 Android network security config 예외 설정 불필요)

---

## 운영 치트시트

```bash
# 앱 로그
sudo journalctl -u billage -f

# 앱 재시작 / 상태
sudo systemctl restart billage
sudo systemctl status billage

# DB 접속
sudo docker exec -it billage-mysql mysql -ubillage -p billage

# Caddy 로그 / 재시작
sudo journalctl -u caddy -f
sudo systemctl restart caddy
```

## DB 백업 / 복구 (운영)

매일 03:30 KST 에 `billage` DB 를 `mysqldump` → gzip → `/var/backups/billage/` 저장, 14일 로컬 보관.
설치는 서버에서 1회: `sudo bash /tmp/deploy/install-backup.sh` (cron `/etc/cron.d/billage-backup` 등록).

```bash
# 수동 백업 즉시 실행
sudo /opt/billage/backup-db.sh
# 백업 로그 / 보관본
sudo tail -f /var/log/billage-backup.log
ls -lt /var/backups/billage/

# 복구 (기존 데이터 덮어씀 — yes 확인 필요)
sudo /opt/billage/restore-db.sh /var/backups/billage/billage-YYYYmmdd-HHMMSS.sql.gz
sudo systemctl restart billage
```

**S3 오프사이트(권장)**: 로컬 백업은 인스턴스/볼륨 유실 시 함께 사라지므로 S3 로 내보낸다.
1) S3 버킷 생성 (`ap-northeast-2`, 예: `billage-db-backup`, 퍼블릭 액세스 차단 유지)
2) IAM 역할 생성 → `s3:PutObject` 를 `arn:aws:s3:::billage-db-backup/mysql/*` 에만 허용 → EC2 인스턴스에 연결
3) 서버에 aws cli 설치 후 `/etc/billage/backup.env` 에 `S3_BUCKET=billage-db-backup` 추가.
   `backup-db.sh` 가 S3_BUCKET 감지 시 자동 업로드. (버킷 수명주기로 30일 후 자동 삭제 권장)

## 도메인이 생기면

`sudo nano /etc/caddy/caddy.env` 에서 `SITE_ADDRESS` 를 `dev-api.내도메인.com` 으로 바꾸고
DNS A 레코드를 EC2 IP 로 지정 → `sudo systemctl restart caddy`. 인증서는 Caddy 가 자동 재발급.

## 남은 이슈 (이후 별도 작업)

- prod 서버·prod 배포 워크플로 (release tag 트리거) — 런칭 시점에 추가
- 탄력적 IP(Elastic IP) 할당 — 재시작 시 IP 고정 (실행 중 인스턴스 연결 시 무료)
- 외부 헬스 모니터링/알림(예: UptimeRobot 무료) + journald `SystemMaxUse` 로그 상한
- Spring Security 설정이 아직 없어 현재는 기본 인증(모든 요청 401)일 수 있음 → auth 도메인 구현 후 permitAll 범위 조정 필요
