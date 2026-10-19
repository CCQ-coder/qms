#!/bin/sh
set -eu

escape_javascript() {
  printf '%s' "$1" | sed 's/\\\\/\\\\\\\\/g; s/"/\\\\"/g; s/\r//g; s/\n/\\\\n/g'
}

cat > /usr/share/nginx/html/runtime-config.js <<EOF
window.__QMS_RUNTIME_CONFIG__ = {
  oidcIssuer: "$(escape_javascript "${QMS_OIDC_ISSUER_URI:-}")",
  oidcClientId: "$(escape_javascript "${QMS_OIDC_CLIENT_ID:-}")",
  oidcScope: "$(escape_javascript "${QMS_OIDC_SCOPE:-openid profile}")"
}
EOF
