#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SHA=${1:?usage: scripts/deploy.sh <sha>}

export COMPOSE="docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml"

PREV=$(cat /opt/myroutine/deployed-sha 2>/dev/null || true)

export IMAGE_TAG=$SHA
if $COMPOSE up -d --wait --wait-timeout 180 && scripts/smoke.sh; then
  echo "$SHA" > /opt/myroutine/deployed-sha
  echo "deployed $SHA"
  exit 0
fi

echo "deploy failed: $SHA" >&2
if [ -n "$PREV" ]; then
  export IMAGE_TAG=$PREV
  $COMPOSE up -d --wait
  echo "rolled back to $PREV" >&2
else
  echo "no previous version to roll back to" >&2
fi
exit 1

