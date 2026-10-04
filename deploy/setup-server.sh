#!/usr/bin/env bash
# EC2(Ubuntu 24.04)를 처음 한 번 준비한다. 다시 돌려도 이미 된 단계는 건너뛴다.
#   bash deploy/setup-server.sh
set -euo pipefail

# 스왑 2GB. 메모리 2GB 서버에서 이미지를 빌드하면 Gradle·npm이 메모리를 다 쓰고 멈춘다
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

# Docker (공식 설치 스크립트). 이 사용자가 sudo 없이 docker를 쓰게 한다 — 다시 접속해야 적용된다
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# 로그와 백업 파일 이름의 시각을 한국 시간으로
sudo timedatectl set-timezone Asia/Seoul

echo "준비 완료. 처음 실행했다면 exit 후 다시 접속해 docker 권한을 적용한다."
