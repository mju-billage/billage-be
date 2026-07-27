#!/usr/bin/env bash
# 서버 자가 점검 (최소 모니터링). cron 5분 간격 실행.
#   - 앱/DB 상태·디스크·메모리를 /var/log/billage-health.log 에 기록
#   - 이상 시 WARN 로그. (systemd 가 billage 자동 재시작하므로 여기선 감지·기록 위주)
#   - MySQL 컨테이너가 unhealthy/중지면 재기동 시도
# 외부 알림(다운 시 메일/푸시)은 UptimeRobot 등 외부 모니터로 보완(README 참고).
set -uo pipefail
LOG=/var/log/billage-health.log
ts(){ date '+%F %T'; }

# 1) 앱 헬스 (localhost). 멈춘 요청이 cron 을 누적시키지 않도록 타임아웃 필수.
code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 5 http://127.0.0.1:8080/actuator/health || echo 000)
case "$code" in 2*|3*|401|403) app="UP($code)";; *) app="DOWN($code)";; esac

# 2) MySQL 컨테이너: 없으면 생성(up -d), 살아있는데 unhealthy 면 restart.
#    (실행 중 컨테이너엔 up -d 로 재시작이 안 되므로 분기)
mstate=$(docker inspect -f '{{.State.Health.Status}}' billage-mysql 2>/dev/null || echo missing)
if [ "$mstate" = "missing" ]; then
  echo "$(ts) WARN mysql 컨테이너 없음 → 생성" >> "$LOG"
  (cd /opt/billage && docker compose --env-file /etc/billage/mysql.env up -d) >> "$LOG" 2>&1
elif [ "$mstate" != "healthy" ]; then
  echo "$(ts) WARN mysql=$mstate → restart" >> "$LOG"
  (cd /opt/billage && docker compose --env-file /etc/billage/mysql.env restart mysql) >> "$LOG" 2>&1
fi

# 3) 디스크 / 메모리
disk=$(df -P / | awk 'NR==2{print $5}' | tr -d '%')
mem=$(free -m | awk '/^Mem:/{printf "%d/%dMB", $3, $2}')
swap=$(free -m | awk '/^Swap:/{printf "%d/%dMB", $3, $2}')

line="$(ts) app=$app mysql=$mstate disk=${disk}% mem=$mem swap=$swap"
echo "$line" >> "$LOG"

# 4) 임계치 경고
[ "$disk" -ge 85 ] && echo "$(ts) WARN 디스크 ${disk}% (>=85%)" >> "$LOG"
[ "$app" != "UP($code)" ] && echo "$(ts) WARN 앱 응답 이상: $app" >> "$LOG"

exit 0
