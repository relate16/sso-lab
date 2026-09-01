#!/bin/sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
static_env="$repo_dir/infra/test/phase9-e2e.env"
runtime_dir=$(mktemp -d)
runtime_env="$runtime_dir/runtime.env"
private_pem="$runtime_dir/oidc-private.pem"
project_name=sso-lab-phase9-e2e

cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    docker compose --project-name "$project_name" \
      --env-file "$static_env" --env-file "$runtime_env" \
      -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.e2e.yml" \
      ps --all || true
    docker compose --project-name "$project_name" \
      --env-file "$static_env" --env-file "$runtime_env" \
      -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.e2e.yml" \
      logs --no-color --tail 200 auth-server admin-server hr-server approval-server caddy e2e \
      || true
  fi
  docker compose --project-name "$project_name" \
    --env-file "$static_env" --env-file "$runtime_env" \
    -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.e2e.yml" \
    down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -rf -- "$runtime_dir"
  return "$status"
}
trap cleanup EXIT INT TERM

umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$private_pem" >/dev/null 2>&1

copy_env() {
  printf '%s=%s\n' "$1" "$2" >> "$runtime_env"
}
random_b64() {
  openssl rand -base64 32 | tr -d '\n'
}

copy_env POSTGRES_PASSWORD "$(random_b64)"
copy_env SSO_EMAIL_ENCRYPTION_KEY "$(random_b64)"
copy_env SSO_EMAIL_LOOKUP_HMAC_KEY "$(random_b64)"
copy_env SSO_OTP_HMAC_KEY "$(random_b64)"
copy_env SSO_TOTP_ENCRYPTION_KEY "$(random_b64)"
copy_env SSO_JWT_PRIVATE_KEY "$(openssl pkcs8 -topk8 -nocrypt -in "$private_pem" -outform DER 2>/dev/null | base64 | tr -d '\n')"
copy_env SSO_JWT_PUBLIC_KEY "$(openssl pkey -in "$private_pem" -pubout -outform DER 2>/dev/null | base64 | tr -d '\n')"
copy_env HR_CLIENT_SECRET "$(random_b64)"
copy_env APPROVAL_CLIENT_SECRET "$(random_b64)"
copy_env ADMIN_CLIENT_SECRET "$(random_b64)"
copy_env ADMIN_INTERNAL_API_SECRET "$(random_b64)"
copy_env TEST_SUPPORT_API_KEY "$(random_b64)"

chmod 600 "$runtime_env"

compose() {
  docker compose --project-name "$project_name" \
    --env-file "$static_env" --env-file "$runtime_env" \
    -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.e2e.yml" "$@"
}

compose config --quiet
compose build
compose up --abort-on-container-exit --exit-code-from e2e e2e
