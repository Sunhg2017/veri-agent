#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

main() {
  cd "$ROOT_DIR/portal-web"

  if [[ -z "${PW_CHROME_CHANNEL:-}" ]]; then
    if [[ "$(uname -s)" == "Darwin" && -d "/Applications/Google Chrome.app" ]]; then
      export PW_CHROME_CHANNEL=chrome
    elif command -v google-chrome >/dev/null 2>&1; then
      export PW_CHROME_CHANNEL=chrome
    fi
  fi

  if [[ "${WP8_FRONTEND_INSTALL_BROWSERS:-0}" == "1" ]]; then
    npx playwright install chromium
  fi

  npm run test:wp8-smoke
}

main "$@"
