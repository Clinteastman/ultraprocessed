#!/bin/bash
# Install the daily SQLite backup cron job on the K12 docker host.
# Idempotent: re-running just keeps the existing line.
#
# Usage: deploy/install-backups.sh

set -euo pipefail

K12=root@192.168.50.60
SCRIPT=/opt/stacks/ultraprocessed/deploy/backup.sh
LOG=/var/log/ultraprocessed-backup.log
CRON_LINE="0 3 * * * $SCRIPT >> $LOG 2>&1"

ssh "$K12" bash -s <<EOF
set -euo pipefail
chmod +x "$SCRIPT"
touch "$LOG"

# Install crontab entry only if missing; preserves any existing user crontab.
( crontab -l 2>/dev/null | grep -vF "$SCRIPT" ; echo "$CRON_LINE" ) | crontab -

echo ">> installed cron:"
crontab -l | grep -F "$SCRIPT"

echo ">> dry-run backup:"
"$SCRIPT"
EOF
