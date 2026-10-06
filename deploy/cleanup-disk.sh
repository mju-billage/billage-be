#!/usr/bin/env bash
# 디스크 정리. cron 으로 주 1회 실행하고, 여유가 부족할 때 손으로도 돌린다.
#
# 목표: 디스크 여유 15GB 이상 유지.
# 지우는 것: 오래된 DB 백업, 로그, Docker 캐시, apt 캐시, 배포가 남긴 빌드 산출물.
# 지우지 않는 것: MinIO 데이터(/var/lib/billage/minio), Docker 볼륨(MySQL 데이터), 사용 중인 이미지,
#   롤백용 직전 jar(/opt/billage/billage.jar.prev).
#   실제 서비스 파일에는 TTL 을 걸지 않는다 — 증빙은 회계 이력이라 오래됐다고 지울 수 없다.
set -uo pipefail

BACKUP_DIR="/var/backups/billage"
if [ -f /etc/billage/backup.env ]; then . /etc/billage/backup.env; fi
RETENTION_DAYS="${RETENTION_DAYS:-7}"
MIN_FREE_GB=15

free_gb() { df -P -BG / | awk 'NR==2{print $4}' | tr -d 'G'; }
ts() { date '+%F %T'; }

echo "[$(ts)] 정리 시작. 여유 $(free_gb)GB"
df -h /
docker system df

# 1) 오래된 DB 백업 (backup-db.sh 도 지우지만, 백업이 실패한 날에는 그쪽 정리가 돌지 않는다)
find "$BACKUP_DIR" -name 'billage-*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete 2>/dev/null \
  | sed 's/^/  삭제(오래된 백업): /'

# 2) Docker: 태그 없는 이미지와 빌드 캐시만. 사용 중이 아닌 이미지 전체(-a)는 지우지 않는다 —
#    컨테이너가 잠시 내려가 있을 때 돌면 MySQL·MinIO 이미지가 지워져 다시 받아야 한다.
docker image prune -f
docker builder prune -af

# 3) 로그: journald 는 14일·200M 상한(journald-billage.conf)이지만 한 번 더 줄인다.
#    Caddy 접근 로그는 Caddy 가 스스로 회전한다(Caddyfile 의 roll_*).
journalctl --vacuum-time=14d --vacuum-size=200M
apt-get clean
for log in /var/log/billage-health.log /var/log/billage-backup.log /var/log/billage-cleanup.log; do
  if [ -f "$log" ] && [ "$(stat -c%s "$log")" -gt $((20 * 1024 * 1024)) ]; then
    tail -n 20000 "$log" > "$log.tmp" && cat "$log.tmp" > "$log" && rm -f "$log.tmp"
    echo "  줄임: $log"
  fi
done

# 4) 배포가 남긴 빌드 산출물(전송 중 임시 jar). 롤백용 billage.jar.prev 하나는 남긴다.
rm -rf /tmp/billage
find /opt/billage -maxdepth 1 -name '*.jar.*' ! -name 'billage.jar.prev' -mtime +7 -print -delete \
  | sed 's/^/  삭제(오래된 jar): /'

echo "[$(ts)] 정리 완료. 여유 $(free_gb)GB / MinIO 데이터 $(du -sh /var/lib/billage/minio 2>/dev/null | cut -f1)"
if [ "$(free_gb)" -lt "$MIN_FREE_GB" ]; then
  echo "[$(ts)] WARN 정리 후에도 여유 ${MIN_FREE_GB}GB 미만. MinIO 버킷 쿼터와 사용량을 확인할 것." >&2
fi
