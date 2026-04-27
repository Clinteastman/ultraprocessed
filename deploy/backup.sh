#!/bin/bash
# Daily SQLite backup for the K12 ultraprocessed deployment.
#
# Snapshots the live SQLite database using `.backup` (consistent even
# while the container is writing) into BACKUP_DIR with a timestamped
# filename, then prunes anything older than RETENTION_DAYS.
#
# Designed to be run as a cron job on the docker host:
#
#   0 3 * * * /opt/stacks/ultraprocessed/deploy/backup.sh >> /var/log/ultraprocessed-backup.log 2>&1
#
# Restore a backup by copying it back into the docker volume:
#
#   docker compose -f /opt/stacks/ultraprocessed/deploy/compose.prod.yml stop backend
#   cp BACKUP_FILE /var/lib/docker/volumes/ultraprocessed-backend-data/_data/ultraprocessed.db
#   docker compose -f ... up -d backend

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/ultraprocessed}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
CONTAINER="${CONTAINER:-ultraprocessed-backend}"
DB_PATH_IN_CONTAINER="${DB_PATH_IN_CONTAINER:-/app/data/ultraprocessed.db}"

mkdir -p "$BACKUP_DIR"

ts="$(date -u '+%Y-%m-%dT%H-%M-%SZ')"
target="$BACKUP_DIR/ultraprocessed-$ts.db"
target_gz="$target.gz"

# `.backup` is the SQLite-supported way to take a consistent online
# snapshot. We pipe it through gzip on the fly to keep disk usage modest.
docker exec "$CONTAINER" sqlite3 "$DB_PATH_IN_CONTAINER" ".backup '/tmp/snap.db'"
docker cp "$CONTAINER:/tmp/snap.db" "$target"
docker exec "$CONTAINER" rm -f /tmp/snap.db
gzip -f "$target"

# Prune old backups
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'ultraprocessed-*.db.gz' -mtime "+${RETENTION_DAYS}" -delete

echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] backed up to $target_gz ($(du -h "$target_gz" | cut -f1))"
