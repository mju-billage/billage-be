#!/usr/bin/env bash
# Billage MySQL 복구. 지정한 백업(.sql.gz)을 billage DB 에 복원한다.
#   sudo /opt/billage/restore-db.sh /var/backups/billage/billage-YYYYmmdd-HHMMSS.sql.gz
#
# 주의: 대상 DB 의 기존 데이터를 덮어쓴다. 실행 전 반드시 확인.
set -euo pipefail

FILE="${1:-}"
MYSQL_ENV="/etc/billage/mysql.env"
CONTAINER="billage-mysql"
DB="billage"

if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
  echo "사용법: $0 <백업파일.sql.gz>" >&2
  echo "가용 백업:" >&2
  ls -1t /var/backups/billage/billage-*.sql.gz 2>/dev/null | head -10 >&2 || true
  exit 1
fi

ROOT_PW="$(grep '^MYSQL_ROOT_PASSWORD=' "$MYSQL_ENV" | cut -d= -f2-)"
[ -n "$ROOT_PW" ] || { echo "ERROR: MYSQL_ROOT_PASSWORD 못 읽음" >&2; exit 1; }

echo "⚠️  '$DB' DB 를 다음 백업으로 덮어씁니다:"
echo "     $FILE"
read -r -p "계속하려면 'yes' 입력: " ans
[ "$ans" = "yes" ] || { echo "취소됨."; exit 1; }

echo "==> 복원 중..."
gunzip -c "$FILE" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
  mysql -uroot --default-character-set=utf8mb4 "$DB"

echo "==> 복원 완료. 앱 재시작 권장: sudo systemctl restart billage"
