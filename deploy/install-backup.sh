#!/usr/bin/env bash
# 백업 스크립트를 서버에 설치하고 cron 을 등록한다. (서버에서 sudo 로 1회 실행)
#   sudo bash /tmp/deploy/install-backup.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> 서버 시각대 KST (cron·로그·백업 파일명 기준)"
timedatectl set-timezone Asia/Seoul
systemctl restart cron

echo "==> 스크립트 배치"
install -m 0755 "$DIR/backup-db.sh"   /opt/billage/backup-db.sh
install -m 0755 "$DIR/restore-db.sh"  /opt/billage/restore-db.sh
install -m 0755 "$DIR/healthcheck.sh" /opt/billage/healthcheck.sh
install -m 0755 "$DIR/cleanup-disk.sh" /opt/billage/cleanup-disk.sh
mkdir -p /var/backups/billage
touch /var/log/billage-backup.log /var/log/billage-health.log /var/log/billage-cleanup.log

echo "==> cron 등록 (백업: 매일 03:30 KST / 자가점검: 5분 간격 / 디스크 정리: 매주 일 04:30 KST)"
cat > /etc/cron.d/billage-backup <<'EOF'
# Billage MySQL 일일 백업. 로그: /var/log/billage-backup.log
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# 시각은 서버 시각대(KST) 기준이다. Debian/Ubuntu 기본 cron 은 CRON_TZ 를 무시하므로
# 서버를 Asia/Seoul 로 맞춰 둔다(이 스크립트 앞부분의 timedatectl).
30 3 * * * root /opt/billage/backup-db.sh >> /var/log/billage-backup.log 2>&1
EOF
cat > /etc/cron.d/billage-health <<'EOF'
# Billage 서버 자가 점검. 로그: /var/log/billage-health.log
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
*/5 * * * * root /opt/billage/healthcheck.sh
EOF
cat > /etc/cron.d/billage-cleanup <<'EOF'
# Billage 디스크 정리(오래된 백업·로그·Docker 캐시). 매주 일요일 04:30 KST. 로그: /var/log/billage-cleanup.log
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
30 4 * * 0 root /opt/billage/cleanup-disk.sh >> /var/log/billage-cleanup.log 2>&1
EOF
chmod 0644 /etc/cron.d/billage-backup /etc/cron.d/billage-health /etc/cron.d/billage-cleanup

echo "==> journald 로그 상한 배치 (200M, 14일)"
mkdir -p /etc/systemd/journald.conf.d
install -m 0644 "$DIR/journald-billage.conf" /etc/systemd/journald.conf.d/00-billage-limit.conf
systemctl restart systemd-journald

echo "==> 완료. 즉시 1회 테스트:"
echo "     sudo /opt/billage/backup-db.sh   # 백업"
echo "     sudo /opt/billage/healthcheck.sh && tail /var/log/billage-health.log   # 자가점검"
echo "   S3 오프사이트: /etc/billage/backup.env 에 S3_BUCKET=... + 인스턴스 역할/aws cli 필요."
