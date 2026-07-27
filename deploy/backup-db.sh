#!/usr/bin/env bash
# Billage MySQL 백업. cron 으로 매일 실행. (deploy/bootstrap.sh 이후 install-backup.sh 로 설치)
#
# 하는 일:
#   1) billage-mysql 컨테이너에서 mysqldump (--single-transaction, 무중단)
#   2) gzip 하여 /var/backups/billage/ 에 저장, 로컬 N일 보관 후 삭제
#   3) (선택) S3_BUCKET 설정 시 aws s3 로 오프사이트 업로드
#
# 설정 파일(선택): /etc/billage/backup.env
#   RETENTION_DAYS=14
#   S3_BUCKET=billage-db-backup        # 있으면 s3://<bucket>/mysql/ 로 업로드
set -euo pipefail

MYSQL_ENV="/etc/billage/mysql.env"
BACKUP_DIR="/var/backups/billage"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
CONTAINER="billage-mysql"
DB="billage"

# 선택 설정 로드
[ -f /etc/billage/backup.env ] && . /etc/billage/backup.env

# root 비밀번호 로드 (mysql.env 에서)
if [ ! -f "$MYSQL_ENV" ]; then
  echo "ERROR: $MYSQL_ENV 없음" >&2; exit 1
fi
ROOT_PW="$(grep '^MYSQL_ROOT_PASSWORD=' "$MYSQL_ENV" | cut -d= -f2-)"
if [ -z "$ROOT_PW" ]; then
  echo "ERROR: MYSQL_ROOT_PASSWORD 를 $MYSQL_ENV 에서 못 읽음" >&2; exit 1
fi

mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/billage-$TS.sql.gz"

echo "[$(date '+%F %T')] 백업 시작 -> $OUT"

# mysqldump: 컨테이너 내부에서 실행, 호스트로 스트림 → gzip
docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
  mysqldump -uroot --single-transaction --quick --routines --triggers \
    --default-character-set=utf8mb4 "$DB" \
  | gzip -c > "$OUT"

# 빈 파일/실패 방어: 최소 크기 검증(gzip 헤더만 있는 경우 방지)
SIZE=$(stat -c%s "$OUT")
if [ "$SIZE" -lt 100 ]; then
  echo "ERROR: 백업 파일이 너무 작음(${SIZE}B). 삭제." >&2
  rm -f "$OUT"; exit 1
fi
echo "[$(date '+%F %T')] 로컬 백업 완료 (${SIZE} bytes)"

# 오프사이트 업로드 (S3_BUCKET 설정 & aws 존재 시)
if [ -n "${S3_BUCKET:-}" ] && command -v aws >/dev/null 2>&1; then
  if aws s3 cp "$OUT" "s3://$S3_BUCKET/mysql/$(basename "$OUT")" --only-show-errors; then
    echo "[$(date '+%F %T')] S3 업로드 완료 -> s3://$S3_BUCKET/mysql/"
  else
    echo "WARN: S3 업로드 실패 (로컬 백업은 유지됨)" >&2
  fi
elif [ -n "${S3_BUCKET:-}" ]; then
  echo "WARN: S3_BUCKET 설정됐으나 aws cli 없음. 로컬만 보관." >&2
fi

# 로컬 로테이션: RETENTION_DAYS 보다 오래된 백업 삭제
find "$BACKUP_DIR" -name 'billage-*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete \
  | sed 's/^/  삭제(오래됨): /' || true

echo "[$(date '+%F %T')] 완료. 현재 보관본:"
ls -1t "$BACKUP_DIR"/billage-*.sql.gz 2>/dev/null | head -5 | sed 's/^/  /'
