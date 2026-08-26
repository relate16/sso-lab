#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../.."

dc() {
  docker compose --env-file infra/test/phase8-prod-config.env \
    -f docker-compose.yml "$@"
}

cleanup() {
  dc down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

expected_site_key=1x00000000000000000000AA

dc run --rm --no-deps --entrypoint sh auth-web -ec \
  "! grep -R -F '$expected_site_key' /usr/share/nginx/html"

dc up -d --no-deps --wait auth-web >/dev/null
enabled_config=$(dc exec -T auth-web sh -ec \
  'if env | grep -Eq "TURNSTILE_SECRET|SITEVERIFY"; then exit 1; fi; cat /usr/share/nginx/html/runtime-config.js')
printf '%s' "$enabled_config" | grep -Fq 'turnstileEnabled:true'
printf '%s' "$enabled_config" | grep -Fq "turnstileSiteKey:\"$expected_site_key\""

auth_image=$(dc images -q auth-web)
test -n "$auth_image"
disabled_config=$(docker run --rm --entrypoint sh \
  -e TURNSTILE_ENABLED=false -e TURNSTILE_SITE_KEY= "$auth_image" -ec \
  '/docker-entrypoint.d/30-sso-lab-runtime-config.sh; cat /usr/share/nginx/html/runtime-config.js')
printf '%s' "$disabled_config" | grep -Fq 'turnstileEnabled:false'
printf '%s' "$disabled_config" | grep -Fq 'turnstileSiteKey:""'

image_env=$(docker image inspect --format '{{json .Config.Env}}' "$auth_image")
if printf '%s' "$image_env" | grep -Eqi 'TURNSTILE_SECRET|SITEVERIFY'; then
  echo 'turnstile server secret reference leaked into auth-web image metadata' >&2
  exit 1
fi

printf 'turnstile_runtime_enabled|pass\n'
printf 'turnstile_runtime_disabled|pass\n'
printf 'turnstile_site_key_not_baked|pass\n'
printf 'turnstile_secret_boundary|pass\n'
