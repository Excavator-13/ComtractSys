#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DATA="false"
RESET_VOLUMES="false"
SKIP_BUILD="false"
SMOKE="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --demo-data)
      DEMO_DATA="true"
      shift
      ;;
    --no-demo-data)
      DEMO_DATA="false"
      shift
      ;;
    --reset-volumes)
      RESET_VOLUMES="true"
      shift
      ;;
    --skip-build)
      SKIP_BUILD="true"
      shift
      ;;
    --smoke)
      SMOKE="true"
      shift
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command not found in PATH: %s\n' "$1" >&2
    exit 1
  fi
}

wait_http() {
  local url="$1"
  local timeout="${2:-120}"
  local start
  start="$(date +%s)"
  while (( "$(date +%s)" - start < timeout )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    if curl -sS -o /dev/null -w '%{http_code}' "$url" 2>/dev/null | grep -Eq '^(2|3|4)[0-9][0-9]$'; then
      return 0
    fi
    sleep 3
  done
  printf 'Timed out waiting for %s\n' "$url" >&2
  return 1
}

require_cmd docker
require_cmd curl

cd "$ROOT"

if [[ "$RESET_VOLUMES" == "true" ]]; then
  echo "[docker-demo] stopping and removing compose volumes"
  docker compose down -v
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  require_cmd mvn
  echo "[docker-demo] packaging backend jar"
  (cd "$ROOT/backend" && mvn package -DskipTests)
fi

echo "[docker-demo] starting docker environment, DEMO_DATA_ENABLED=$DEMO_DATA"
DEMO_DATA_ENABLED="$DEMO_DATA" docker compose up -d --build

wait_http "http://localhost:18080/api/v1/auth/me" 180
wait_http "http://localhost:5173" 120

cat <<EOF

Docker demo environment is ready:
  Frontend: http://localhost:5173
  Backend:  http://localhost:18080
  Swagger:  http://localhost:18080/swagger-ui/index.html
  Demo data: $DEMO_DATA

Accounts:
  admin / 123456
  demo_manager / 123456      (when --demo-data is used)
  demo_drafter / 123456      (when --demo-data is used)
  demo_countersign / 123456  (when --demo-data is used)
  demo_approver / 123456     (when --demo-data is used)
  demo_signer / 123456       (when --demo-data is used)

Stop:
  docker compose down
EOF

if [[ "$SMOKE" == "true" ]]; then
  CONTRACTSYS_BACKEND_URL="http://localhost:18080" CONTRACTSYS_FRONTEND_URL="http://localhost:5173" pwsh "$ROOT/scripts/docker-smoke.ps1"
fi
