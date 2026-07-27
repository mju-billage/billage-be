#!/usr/bin/env bash
# EC2(Ubuntu 24.04) 최초 1회 실행. 서버에 SSH 접속 후 아래처럼 실행:
#   scp deploy/* ubuntu@52.78.148.114:/tmp/deploy/    (또는 git clone)
#   sudo bash /tmp/deploy/bootstrap.sh
#
# 하는 일: 스왑 생성, JRE21/Docker/Caddy 설치, billage 유저·디렉터리 생성,
#          systemd 유닛·Caddyfile 배치. (실제 비밀값 env 파일은 수동 작성)
set -euo pipefail

echo "==> 1. 스왑 2G 생성 (1GB RAM 보완)"
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> 2. 패키지 설치 (JRE21, Docker, Caddy)"
apt-get update -y
apt-get install -y openjdk-21-jre-headless curl ca-certificates

# Docker
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi

# Caddy (공식 apt 저장소)
if ! command -v caddy >/dev/null; then
  apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi

echo "==> 3. billage 유저 · 디렉터리"
id -u billage >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin billage
mkdir -p /opt/billage /etc/billage /var/log/caddy
usermod -aG docker billage || true

echo "==> 4. 설정 파일 배치"
DIR="$(cd "$(dirname "$0")" && pwd)"
cp "$DIR/billage.service" /etc/systemd/system/billage.service
cp "$DIR/compose.yaml"   /opt/billage/compose.yaml
cp "$DIR/Caddyfile"      /etc/caddy/Caddyfile

# Caddy 가 caddy.env 를 읽도록 systemd drop-in 추가
mkdir -p /etc/systemd/system/caddy.service.d
cat > /etc/systemd/system/caddy.service.d/env.conf <<'EOF'
[Service]
EnvironmentFile=/etc/caddy/caddy.env
EOF

# env 예시 파일 배치 (실제 값은 수동으로 채운다)
for f in billage.env mysql.env; do
  [ -f "/etc/billage/$f" ] || cp "$DIR/$f.example" "/etc/billage/$f"
done
[ -f /etc/caddy/caddy.env ] || cp "$DIR/caddy.env.example" /etc/caddy/caddy.env
chmod 600 /etc/billage/*.env /etc/caddy/caddy.env
chown -R billage:billage /opt/billage /etc/billage

systemctl daemon-reload

cat <<'DONE'

==> 부트스트랩 완료. 이제 수동으로 아래를 마무리하세요:

  1) 비밀값 채우기:
       sudo nano /etc/billage/mysql.env     # MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD
       sudo nano /etc/billage/billage.env   # DB_PASSWORD (= MYSQL_PASSWORD 와 동일)
       sudo nano /etc/caddy/caddy.env       # SITE_ADDRESS = <IP를->로>.nip.io

  2) MySQL 기동:
       cd /opt/billage && sudo docker compose --env-file /etc/billage/mysql.env up -d

  3) Caddy 재시작:
       sudo systemctl restart caddy

  4) 첫 배포는 GitHub Actions(develop push) 가 jar 를 올리고 billage 를 start 한다.
     수동 활성화만 한번 해두기:
       sudo systemctl enable billage

DONE
