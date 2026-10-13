#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f compose.local.yaml ]]; then
  echo "Missing compose.local.yaml: copy compose.local.example.yaml and set site-specific values." >&2
  exit 1
fi
case "${1:-status}" in
 start)
  if [[ -z "${HTTPS_PROXY:-}" ]] && curl -s --max-time 2 http://127.0.0.1:10808 >/dev/null; then
    export HTTP_PROXY=http://127.0.0.1:10808 HTTPS_PROXY=http://127.0.0.1:10808
    export NO_PROXY=localhost,127.0.0.1
  fi
  docker compose -f compose.local.yaml up -d --build ;;
 stop) docker compose -f compose.local.yaml stop ;;
 status) docker compose -f compose.local.yaml ps -a ;;
 logs) docker compose -f compose.local.yaml logs --tail=100 ;;
 *) echo "Usage: $0 {start|stop|status|logs}"; exit 1 ;;
esac
