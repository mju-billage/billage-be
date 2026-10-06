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
scp -i billage.pem -r deploy ubuntu@52.78.148.114:/tmp/

# 서버
ssh -i billage.pem ubuntu@52.78.148.114
sudo bash /tmp/deploy/bootstrap.sh
```

부트스트랩이 스왑·JRE21·Docker·Caddy 설치와 파일 배치까지 해준다. 이후 안내대로 비밀값을 채운다:

```bash
sudo nano /etc/billage/mysql.env     # MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD
sudo nano /etc/billage/billage.env   # DB_PASSWORD = MYSQL_PASSWORD 와 동일하게
sudo nano /etc/caddy/caddy.env       # SITE_ADDRESS = <IP의 .을 -로>.nip.io  예) 52-78-148-114.nip.io

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
| `GMAIL_USERNAME` | 메일 발송용 Gmail 주소 |
| `GMAIL_APP_PASSWORD` | 그 계정의 앱 비밀번호 (계정 비밀번호가 아니다) |

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

매일 03:30 KST 에 `billage` DB 를 `mysqldump` → gzip → `/var/backups/billage/` 저장, 7일 로컬 보관.
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

**메일 발송 Gmail SMTP**: 회원가입 이메일 인증 코드를 보낸다(`smtp.gmail.com:587`, STARTTLS).

- 발신 도메인이 없어 Gmail 계정으로 보낸다. 받는 사람에게는 그 Gmail 주소가 발신자로 보인다.
- 인증은 Gmail 주소와 **앱 비밀번호**(계정 비밀번호가 아니다). 구글 계정에서 2단계 인증을 켠 뒤 발급한다.
- GitHub Actions Secret `GMAIL_USERNAME` / `GMAIL_APP_PASSWORD` 에 두면 배포 때 `/etc/billage/billage.env` 에 들어간다. 저장소에는 커밋하지 않는다.
- 앱 설정: `BILLAGE_MAIL_SENDER=SMTP`. dev·prod 프로파일 기본값이 `SMTP` 이고, **계정이나 앱 비밀번호가 비면 기동이 실패한다** — 환경변수를 빠뜨렸는데 조용히 메일이 안 나가는 상황을 막으려는 것이다.
- Gmail 무료 계정은 **하루 500통**까지다. 넘으면 그날은 발송이 거부된다.
- 준비 전 임시로 끄려면 dev 에서 `BILLAGE_MAIL_SENDER=LOG` — 기동 시 경고가 남고 메일은 나가지 않는다. prod 에서 LOG 로 두면 **앱이 기동하지 않는다**(조용히 메일이 안 나가는 것을 막기 위함).

**업로드 파일 MinIO**: 같은 서버의 Docker 컨테이너 `billage-minio` (`deploy/compose.minio.yaml`). AWS S3 는 쓰지 않는다.

- **이미지**: `pgsty/silo:RELEASE.2026-09-16T00-00-00Z` (digest 고정). 공식 `minio/minio` 이미지는 Docker Hub·Quay 에서 내려갔고 원본 저장소도 보관 상태라, 유지보수가 이어지는 포크를 쓴다. S3 API·`mc`·환경변수 이름은 MinIO 와 같다. `latest` 금지 — 올릴 때는 태그와 digest 를 함께 바꾼다.
- **포트**: `127.0.0.1:9000`(S3 API), `127.0.0.1:9001`(Console). **외부에 열지 않고 Caddy 로도 연결하지 않는다.** Console 이 필요하면 SSH 터널(`ssh -L 9001:127.0.0.1:9001 ...`)로 본다.
- **데이터**: 호스트 `/var/lib/billage/minio` (컨테이너 `/data`). 컨테이너·이미지를 지워도 남는다.
- **버킷**: `billage` (비공개). 환경 구분은 프리픽스 — dev 는 `dev/`. 하드 쿼터 25GiB(아래 "디스크 용량 정책").
- **백업 없음(알고 감수하는 위험)**: 업로드 파일은 이 VM 디스크 한 곳에만 있다. 외부 저장소를 두지 않기로 했으므로(2026-10-06) VM 이나 디스크를 잃으면 DB 는 덤프로 되살려도 **파일 본문은 복구할 수 없다.** DB 덤프도 오프사이트 업로드가 꺼져 있으면 같은 VM 에만 남는다. 실사용자 데이터를 받기 전(런칭 전)에 다시 정한다 — 붙일 때는 `mc mirror local/billage <외부>` 를 cron 으로 돌리면 된다.
- **계정**: 관리자(root)는 `/etc/billage/minio.env`(600), 앱은 `billage` 버킷의 객체 읽기·쓰기·삭제만 되는 전용 키(`billage-app` 정책)를 `/etc/billage/billage.env` 에 둔다. 둘 다 커밋 금지.
- **앱 설정**: `BILLAGE_FILE_STORAGE=S3`, `S3_ENDPOINT=http://127.0.0.1:9000`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET=billage`, `S3_REGION=us-east-1`, `S3_PREFIX=dev`. AWS SDK for S3 를 그대로 쓰고 엔드포인트·path-style·액세스 키만 지정한다.
- **다운로드**: `GET /api/v1/files/{id}/content` 가 권한 확인 후 MinIO 에서 읽어 200 으로 직접 스트리밍한다(presigned URL·302 없음).

```bash
# 처음 띄우기
sudo install -d -m 0750 /var/lib/billage/minio
sudo cp deploy/minio.env.example /etc/billage/minio.env && sudo chmod 600 /etc/billage/minio.env   # 값 채우기
cd /opt/billage && sudo docker compose -f compose.minio.yaml --env-file /etc/billage/minio.env up -d

# 상태 / 재시작 / 로그
sudo docker ps --filter name=billage-minio
cd /opt/billage && sudo docker compose -f compose.minio.yaml --env-file /etc/billage/minio.env restart minio
sudo docker logs --tail 100 -f billage-minio

# 관리 명령(mc 는 이미지에 들어 있다). 관리자 계정은 minio.env 에서 읽는다.
sudo bash -c '. /etc/billage/minio.env; docker exec -e MC_HOST_local="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@127.0.0.1:9000" billage-minio mc du local/billage'
#   mc ls -r local/billage/dev/          객체 목록
#   mc quota info local/billage          쿼터 확인
#   mc quota set local/billage --size 25GiB
#   mc admin info local                  서버·디스크 상태
```

버킷·앱 전용 키를 새로 만들 때: `mc mb local/billage` → `mc quota set local/billage --size 25GiB` →
`mc admin policy create local billage-app <정책 json>` → `mc admin user add local <키> <시크릿>` →
`mc admin policy attach local billage-app --user <키>`.

**기존 AWS S3 파일 이관(보류 — 필요해지면 실행)**: BlueRack 으로 옮기며 DB 를 빈 상태로 새로 시작해, 옛 객체를 가리키는 파일 행이 없다.
옛 DB 를 복원하게 되면 그때 같은 키 구조(`dev/...`)로 복사한다. **S3 원본은 지우지 않는다.**

```bash
# rclone 설정(~/.config/rclone/rclone.conf, 커밋 금지)
#   [aws]   type=s3 provider=AWS   access_key_id=... secret_access_key=... region=ap-northeast-2
#   [minio] type=s3 provider=Minio access_key_id=<관리자> secret_access_key=... endpoint=http://127.0.0.1:9000
rclone size aws:billage-files-442908904609/dev                      # 원본 개수·용량 (쿼터 25GiB 안인지 먼저 확인)
rclone copy aws:billage-files-442908904609/dev minio:billage/dev --progress
rclone size minio:billage/dev                                       # 개수·용량이 원본과 같은지
rclone check aws:billage-files-442908904609/dev minio:billage/dev   # 객체별 크기·해시 비교, 차이 0 이어야 한다
```

## 디스크 용량 정책 (64GB VM)

목표: **여유 15GB 이상을 항상 유지.** 디스크가 차면 MySQL 쓰기부터 실패하므로 파일 저장소가 디스크를 다 쓰지 못하게 막는다.

| 항목 | 2026-10-06 실측 | 상한(예산) | 지키는 방법 |
|---|---|---|---|
| OS·패키지·Java·스왑 4G | 8.0G | 10G | apt 캐시 주 1회 정리 |
| Docker 이미지·레이어·캐시 | 1.1G | 3G | 태그 없는 이미지·빌드 캐시 주 1회 정리, 컨테이너 로그 10MB×3 |
| MySQL 데이터 | 0.2G | 3G | 넘어가면 예산 재검토 |
| DB 백업 | 1M 미만 | 0.5G | **7일 보관** |
| 로그(journald·Caddy·점검) | 0.03G | 0.5G | journald 200M·14일, Caddy 20MB×5·14일, 점검 로그 20MB 넘으면 줄임 |
| jar·빌드 산출물 | 0.1G | 0.5G | 현재 jar + 롤백용 1개만, `/tmp/billage` 정리 |
| **업로드 파일(MinIO)** | 0 | **25G** | 버킷 하드 쿼터 25GiB, **TTL 없음** |
| 여유 | 50.5G | **15G 이상** | 자가점검이 80% 이상·15GB 미만이면 WARN |

파일시스템 62.7GiB 중 root 예약 3.2GiB 를 빼면 쓸 수 있는 건 59.5GiB — 위 예산 합(42.5G) + 여유 15G 가 그 안에 들어온다.
**권장 최대 파일 저장량은 25GiB** 다(10MB 파일 2,500개, 보통 2~3MB 인 사진이면 약 1만 개).

- **쿼터에 닿으면**: MinIO 가 업로드만 거부한다 → 앱은 `FILE_UPLOAD_FAILED`. 조회·삭제·나머지 API 는 정상. 쿼터는 MinIO 가 주기적으로 집계한 사용량으로 판정해 약간 넘칠 수 있으므로, 쿼터와 여유 15GB 사이에 간격을 뒀다.
- **디스크 자체가 차면**: MySQL 쓰기 실패(가입·내역 등록 등 500), DB 백업 실패, 로그 유실. 쿼터와 경고는 여기까지 가지 않게 하려는 것이다.
- **감지**: `healthcheck.sh`(5분)가 `/var/log/billage-health.log` 에 `disk=…% free=…G minio_data=…` 를 남기고, 사용 80% 이상 또는 여유 15GB 미만이면 `WARN`. `sudo grep WARN /var/log/billage-health.log | tail`.
- **정리**: `cleanup-disk.sh`(매주 일 04:30 KST, 로그 `/var/log/billage-cleanup.log`). 급하면 `sudo /opt/billage/cleanup-disk.sh`.
- **지워도 되는 것**: 7일 넘은 DB 백업, journald·Caddy·점검 로그, 태그 없는 Docker 이미지·빌드 캐시, apt 캐시, `/tmp/billage`.
- **지우면 안 되는 것**: `/var/lib/billage/minio`(증빙 원본 — DB 행과 짝이라 파일만 지우면 이미지가 깨진다), Docker 볼륨 `billage_mysql-data`, 사용 중인 이미지, `/etc/billage/*.env`, 최근 7일 백업. `docker system prune -a --volumes` 는 쓰지 않는다.
- 25GiB 를 넘겨야 하면 쿼터를 올리기 전에 디스크를 늘리거나 파일을 외부 저장소로 옮기는 것을 먼저 검토한다.

```bash
df -h /
sudo du -sh /var/lib/billage/minio /var/backups/billage /var/log /var/lib/docker
sudo docker system df
journalctl --disk-usage
```

**S3 오프사이트(적용됨)**: 로컬 + S3 이중 보관.
- 버킷: `s3://billage-db-backup-442908904609/mysql/` (`ap-northeast-2`, 퍼블릭 차단, SSE-S3, 수명주기 30일 자동삭제)
- 인증: EC2 인스턴스 프로파일 `billage-backup-profile`(역할 `billage-backup-role`, 버킷에 `s3:PutObject`만) — **서버에 액세스 키 저장 없음**
- 서버 `/etc/billage/backup.env` 에 `S3_BUCKET=billage-db-backup-442908904609` 설정됨 → `backup-db.sh` 가 자동 업로드
- 복구 시 다운로드: `aws s3 cp s3://billage-db-backup-442908904609/mysql/<파일> .` 후 `restore-db.sh`

## 운영 하드닝 (적용 완료)

- **외부 접근 차단**: 앱은 `SERVER_ADDRESS=127.0.0.1` 로 로컬만 리슨(+보안그룹), MySQL 은 `127.0.0.1:3306` 바인딩. 8080/3306 외부 노출 없음.
- **메모리 상한** (1GB+스왑2G 예산): JVM `-Xmx384m`, MySQL `mem_limit: 360m` + `innodb-buffer-pool 128M` + `performance-schema OFF`. 나머지는 Docker/Caddy/OS + 스왑.
- **배포 안전장치**: 헬스체크(2xx/3xx=성공, 타임아웃 적용) 실패 시 **이전 jar 로 자동 롤백 + 롤백본 헬스 재확인**(`billage.jar.prev`).
- **재시작/로그**: `Restart=always`, journald `SystemMaxUse=200M`·14일, Caddy 접근 로그 20MB×5 회전, 자가점검 cron 5분(`/var/log/billage-health.log`), 디스크 정리 cron 주 1회.
- **백업/복구**: 위 "DB 백업 / 복구" 참고. 복구 왕복 테스트 검증됨.

### 최소 모니터링

- 서버 자가점검: `/opt/billage/healthcheck.sh`(cron 5분) — 앱/DB/MinIO/디스크/메모리 로깅, MySQL·MinIO 이상 시 재기동, 디스크 80% 이상·여유 15GB 미만 WARN.
- **외부 알림(권장, 별도 작업)**: [UptimeRobot](https://uptimerobot.com) 무료 계정 → HTTP(s) 모니터로 `https://52-78-148-114.nip.io/actuator/health` 5분 감시 → 다운 시 이메일 알림. (서버 자체가 죽으면 내부 cron 은 못 알리므로 외부 감시가 필요)

## 도메인이 생기면

`sudo nano /etc/caddy/caddy.env` 에서 `SITE_ADDRESS` 를 `dev-api.내도메인.com` 으로 바꾸고
DNS A 레코드를 EC2 IP 로 지정 → `sudo systemctl restart caddy`. 인증서는 Caddy 가 자동 재발급.

## 남은 이슈 (이후 별도 작업)

- prod 서버·prod 배포 워크플로 (release tag 트리거) — 런칭 시점에 추가
- 외부 헬스 모니터링(UptimeRobot) 실제 등록 — 위 안내대로 계정 생성 필요
- ⚠️ **접근 제어 없음**: 현재 `SecurityConfig` 가 HTTP Basic 비활성 + `anyRequest().permitAll()` → **모든 엔드포인트가 공개**됨(401 아님). auth 도메인 구현 시 인증/인가 규칙 적용 필요
- (완료) 탄력적 IP 고정 `52.78.148.114` / S3 오프사이트 백업 / 운영 하드닝
