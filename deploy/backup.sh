#!/usr/bin/env bash
# DB를 덤프해 ~/backups에 7일치를 남긴다. 서버의 crontab에 등록해 매일 돌린다:
#   0 4 * * * /home/ubuntu/graduation-project/deploy/backup.sh >> /home/ubuntu/backups/backup.log 2>&1
# 되살리기:  gunzip -c <파일> | docker compose exec -T postgres psql -U fitness -d fitness
set -euo pipefail
cd "$(dirname "$0")"

DIR="$HOME/backups"
mkdir -p "$DIR"
FILE="$DIR/fitness-$(date +%Y%m%d-%H%M).sql.gz"

docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$FILE"
find "$DIR" -name 'fitness-*.sql.gz' -mtime +7 -delete
echo "$(date '+%F %T') $FILE $(du -h "$FILE" | cut -f1)"
