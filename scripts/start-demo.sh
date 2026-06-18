#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
RUN_DIR="$ROOT/output/demo-run"

PROFILE="${PROFILE:-functional-test}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
SKIP_INSTALL="${SKIP_INSTALL:-false}"
OPEN_BROWSER="${OPEN_BROWSER:-false}"

BACKEND_LOG="$RUN_DIR/backend.out.log"
BACKEND_ERR="$RUN_DIR/backend.err.log"
FRONTEND_LOG="$RUN_DIR/frontend.out.log"
FRONTEND_ERR="$RUN_DIR/frontend.err.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"

log() {
  printf '[demo] %s\n' "$1"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command not found in PATH: %s\n' "$1" >&2
    exit 1
  fi
}

stop_port() {
  local port="$1"
  local name="$2"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    return
  fi
  log "stopping process(es) using $name port $port: $pids"
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  sleep 2
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
  fi
}

wait_http() {
  local url="$1"
  local timeout="${2:-90}"
  local start
  start="$(date +%s)"
  while (( "$(date +%s)" - start < timeout )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    # Auth endpoints may return 401/403 and still prove the server is ready.
    if curl -sS -o /dev/null -w '%{http_code}' "$url" 2>/dev/null | grep -Eq '^(2|3|4)[0-9][0-9]$'; then
      return 0
    fi
    sleep 2
  done
  printf 'Timed out waiting for %s\n' "$url" >&2
  return 1
}

mkdir -p "$RUN_DIR"

if [[ -x "$ROOT/scripts/stop-demo.sh" ]]; then
  log "stopping previously recorded demo services"
  "$ROOT/scripts/stop-demo.sh"
fi

require_cmd mvn
require_cmd npm
require_cmd curl
require_cmd lsof

stop_port "$BACKEND_PORT" "backend"
stop_port "$FRONTEND_PORT" "frontend"

if [[ "$SKIP_INSTALL" != "true" && ! -d "$FRONTEND_DIR/node_modules" ]]; then
  log "installing frontend dependencies"
  (cd "$FRONTEND_DIR" && npm install)
fi

log "starting backend on http://localhost:$BACKEND_PORT with profile '$PROFILE'"
(
  cd "$BACKEND_DIR"
  export DEMO_DATA_ENABLED=true
  mvn spring-boot:run "-Dspring-boot.run.profiles=$PROFILE" "-Dspring-boot.run.arguments=--server.port=$BACKEND_PORT"
) >"$BACKEND_LOG" 2>"$BACKEND_ERR" &
echo "$!" > "$BACKEND_PID_FILE"

wait_http "http://localhost:$BACKEND_PORT/api/v1/auth/me" 120

log "starting frontend on http://localhost:$FRONTEND_PORT"
(
  cd "$FRONTEND_DIR"
  export VITE_BACKEND_TARGET="http://localhost:$BACKEND_PORT"
  npm run dev -- --host 0.0.0.0 --port "$FRONTEND_PORT"
) >"$FRONTEND_LOG" 2>"$FRONTEND_ERR" &
echo "$!" > "$FRONTEND_PID_FILE"

wait_http "http://localhost:$FRONTEND_PORT" 90

cat <<EOF

Demo environment is ready:
  Frontend: http://localhost:$FRONTEND_PORT
  Backend:  http://localhost:$BACKEND_PORT

Demo accounts, password 123456:
  admin
  demo_manager
  demo_drafter
  demo_countersign
  demo_approver
  demo_signer
  demo_viewer

Logs:
  $BACKEND_LOG
  $BACKEND_ERR
  $FRONTEND_LOG
  $FRONTEND_ERR

Stop:
  ./scripts/stop-demo.sh
EOF

if [[ "$OPEN_BROWSER" == "true" ]]; then
  open "http://localhost:$FRONTEND_PORT" >/dev/null 2>&1 || true
fi
