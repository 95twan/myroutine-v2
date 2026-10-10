#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
$COMPOSE exec -T app curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/api/products > /dev/null
