#!/bin/sh
set -eu

enabled=${TURNSTILE_ENABLED:-false}
site_key=${TURNSTILE_SITE_KEY:-}

case "$enabled" in
  true|false) ;;
  *)
    echo "TURNSTILE_ENABLED must be true or false" >&2
    exit 1
    ;;
esac

if [ "$enabled" = true ]; then
  if ! printf '%s' "$site_key" | grep -Eq '^[A-Za-z0-9_-]+$'; then
    echo "TURNSTILE_SITE_KEY must be a non-empty public Site Key when Turnstile is enabled" >&2
    exit 1
  fi
else
  site_key=
fi

umask 022
printf 'window.ssoLabRuntimeConfig=Object.freeze({turnstileEnabled:%s,turnstileSiteKey:"%s"});\n' \
  "$enabled" "$site_key" > /usr/share/nginx/html/runtime-config.js
