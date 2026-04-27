#!/bin/bash
# Bug-test redeploy helper for the K12 docker host.
# Push your changes first, then run this. By default it:
#   1. Watches the latest GitHub Actions build to completion
#   2. Pulls the repo + the new image on K12
#   3. Recreates the container
#   4. Tails the last 20 lines so you can see startup
#
# Usage:
#   deploy/redeploy.sh                  # full cycle
#   deploy/redeploy.sh --no-wait        # skip the Actions wait (you've already waited)
#   deploy/redeploy.sh --no-pull        # compose-only changes; don't pull image
#   deploy/redeploy.sh --tail 100       # tail more lines after deploy
#
# Requires: gh CLI authed, ssh access to root@192.168.50.60.

set -euo pipefail

K12=root@192.168.50.60
STACK=/opt/stacks/ultraprocessed
WAIT_FOR_ACTIONS=1
DO_PULL=1
TAIL_LINES=20

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-wait) WAIT_FOR_ACTIONS=0; shift ;;
    --no-pull) DO_PULL=0; shift ;;
    --tail) TAIL_LINES="$2"; shift 2 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1"; exit 2 ;;
  esac
done

if [[ "$WAIT_FOR_ACTIONS" == "1" ]]; then
  RUN_ID=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
  echo ">> waiting for Actions run $RUN_ID"
  gh run watch --exit-status "$RUN_ID"
fi

PULL_CMD=""
if [[ "$DO_PULL" == "1" ]]; then
  PULL_CMD="docker compose -f compose.prod.yml pull && "
fi

echo ">> deploying on $K12"
ssh "$K12" "cd $STACK && git pull && cd deploy && \
  ${PULL_CMD}docker compose -f compose.prod.yml up -d && \
  echo --- && \
  docker logs --tail=$TAIL_LINES ultraprocessed-backend"

echo ">> health check"
curl -fsS https://ultraprocessed.mossom.co.uk/api/v1/health && echo

echo ">> running git SHA on container:"
ssh "$K12" "docker inspect ultraprocessed-backend --format '{{index .Config.Labels \"org.opencontainers.image.revision\"}}' || true"
