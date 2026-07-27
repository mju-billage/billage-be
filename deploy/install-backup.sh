#!/usr/bin/env bash
# 백업 스크립트를 서버에 설치하고 cron 을 등록한다. (서버에서 sudo 로 1회 실행)
#   sudo bash /tmp/deploy/install-backup.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> 백업 스크립트 배치"
install -m 0755 "$DIR/backup-db.sh"  /opt/billage/backup-db.sh
install -m 0755 "$DIR/restore-db.sh" /opt/billage/restore-db.sh
mkdir -p /var/backups/billage

echo "==> cron 등록 (매일 18:30 UTC = 03:30 KST)"
cat > /etc/cron.d/billage-backup <<'EOF'
# Billage MySQL 일일 백업. 로그: /var/log/billage-backup.log
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
30 18 * * * root /opt/billage/backup-db.sh >> /var/log/billage-backup.log 2>&1
EOF
chmod 0644 /etc/cron.d/billage-backup
touch /var/log/billage-backup.log

echo "==> 완료. 즉시 1회 테스트:"
echo "     sudo /opt/billage/backup-db.sh"
echo "   S3 오프사이트를 켜려면 /etc/billage/backup.env 에 S3_BUCKET=... 추가 후 aws cli/역할 설정."
