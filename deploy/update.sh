#!/usr/bin/env bash
# 받아 온 코드로 이미지를 다시 빌드하고 바뀐 컨테이너만 갈아 끼운다. 코드는 미리 받아 둔다:
#   git pull && bash deploy/update.sh
# GitHub Actions(deploy.yml)도 커밋을 받은 뒤 이걸 부른다.
set -euo pipefail
cd "$(dirname "$0")"

# 빌드를 먼저 끝내고 갈아 끼운다. 빌드하는 몇 분 동안은 이전 버전이 계속 응답한다
docker compose build --quiet
docker compose up -d --remove-orphans

# 디스크 20GB 서버다. 교체된 옛 이미지와 사흘 넘은 빌드 캐시를 지운다
docker image prune -f > /dev/null
docker builder prune -f --filter until=72h > /dev/null

docker compose ps --format '{{.Service}}\t{{.Status}}'
