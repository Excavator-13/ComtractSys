#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/output/demo-run"

log() {
  printf '[demo] %s\n' "$1"
}

stop_pid() {
  local pid="$1"
  if [[ -z "$pid" ]]; then
    return
  fi
  if kill -0 "$pid" >/dev/null 2>&1; then
    log "stopping process $pid"
    pkill -TERM -P "$pid" >/dev/null 2>&1 || true
    kill "$pid" >/dev/null 2>&1 || true
  fi
}

if [[ ! -d "$RUN_DIR" ]]; then
  log "no recorded demo run found"
  exit 0
fi

find "$RUN_DIR" -maxdepth 1 -name '*.pid' -type f | while read -r pid_file; do
  pid="$(head -n 1 "$pid_file" | tr -cd '0-9')"
  stop_pid "$pid"
  rm -f "$pid_file"
done

sleep 1
log "stopped recorded demo processes"
