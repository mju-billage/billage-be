#!/usr/bin/env bash
# 백업 스크립트를 서버에 설치하고 cron 을 등록한다. (서버에서 sudo 로 1회 실행)
#   sudo bash /tmp/deploy/install-backup.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> 스크립트 배치"
install -m 0755 "$DIR/backup-db.sh"   /opt/billage/backup-db.sh
install -m 0755 "$DIR/restore-db.sh"  /opt/billage/restore-db.sh
install -m 0755 "$DIR/healthcheck.sh" /opt/billage/healthcheck.sh
mkdir -p /var/backups/billage
touch /var/log/billage-backup.log /var/log/billage-health.log

echo "==> cron 등록 (백업: 매일 03:30 KST / 자가점검: 5분 간격)"
cat > /etc/cron.d/billage-backup <<'EOF'
# Billage MySQL 일일 백업. 로그: /var/log/billage-backup.log
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# 서버 TZ 와 무관하게 KST 03:30 을 보장한다.
CRON_TZ=Asia/Seoul
30 3 * * * root /opt/billage/backup-db.sh >> /var/log/billage-backup.log 2>&1
EOF
cat > /etc/cron.d/billage-health <<'EOF'
# Billage 서버 자가 점검. 로그: /var/log/billage-health.log
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
*/5 * * * * root /opt/billage/healthcheck.sh
EOF
chmod 0644 /etc/cron.d/billage-backup /etc/cron.d/billage-health

echo "==> journald 로그 상한 배치 (200M)"
mkdir -p /etc/systemd/journald.conf.d
install -m 0644 "$DIR/journald-billage.conf" /etc/systemd/journald.conf.d/00-billage-limit.conf
systemctl restart systemd-journald

echo "==> 완료. 즉시 1회 테스트:"
echo "     sudo /opt/billage/backup-db.sh   # 백업"
echo "     sudo /opt/billage/healthcheck.sh && tail /var/log/billage-health.log   # 자가점검"
echo "   S3 오프사이트: /etc/billage/backup.env 에 S3_BUCKET=... + 인스턴스 역할/aws cli 필요."
