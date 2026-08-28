#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../.."

project="sso-lab-phase8-health-${GITHUB_RUN_ID:-local}-$$"
fixture=$(mktemp -d)
secret_dir="$fixture/secrets"
runtime_uid=$(id -u)
runtime_gid=$(id -g)
base_image_prefix=${PHASE8_BASE_IMAGE_PREFIX:-sso-lab-phase8-validation}
base_image_tag=${PHASE8_BASE_IMAGE_TAG:-latest}
release_tag=phase8-health-test
image_registry=phase8-health.local
image_namespace=sso-lab
temporary_refs=""

compose() {
  COMPOSE_PROJECT_NAME="$project" \
  BACKEND_RUNTIME_UID="$runtime_uid" \
  BACKEND_RUNTIME_GID="$runtime_gid" \
  PRODUCTION_SECRET_DIR="$secret_dir" \
  IMAGE_REGISTRY="$image_registry" \
  IMAGE_NAMESPACE="$image_namespace" \
  IMAGE_TAG="$release_tag" \
  PUBLIC_NETWORK_SUBNET=172.31.253.0/24 \
  CADDY_IPV4_ADDRESS=172.31.253.254 \
  GMAIL_SMTP_USERNAME=phase8-test@example.invalid \
  GMAIL_SMTP_FROM=phase8-test@example.invalid \
  BOOTSTRAP_ADMIN_ENABLED=false \
  docker compose --project-name "$project" \
    --env-file infra/test/phase8-prod-config.env \
    -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

cleanup() {
  exit_code=$?
  set +e
  if [ "$exit_code" -ne 0 ]; then
    compose ps --all
    compose logs --no-color auth-server admin-server hr-server approval-server \
      | grep -E 'ERROR|Caused by:|Exception|unavailable|failed|Failure' \
      | tail -80 \
      | sed -E 's/(secret|token|password|code)=[^ ,]+/\1=[REDACTED]/Ig'
  fi
  compose down --remove-orphans >/dev/null 2>&1
  temporary_volume="${project}_postgres-data"
  if docker volume inspect "$temporary_volume" >/dev/null 2>&1; then
    users=$(docker ps -aq --filter volume="$temporary_volume" | wc -l)
    if [ "$users" -eq 0 ]; then
      docker volume rm "$temporary_volume" >/dev/null 2>&1
    fi
  fi
  for ref in $temporary_refs; do
    docker image rm "$ref" >/dev/null 2>&1
  done
  case "$fixture" in
    /tmp/*) rm -r -- "$fixture" ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

install -d -m 700 "$secret_dir"
for name in email-encryption-key email-lookup-hmac-key otp-hmac-key \
  totp-encryption-key; do
  python3 - "$secret_dir/$name" <<'PY'
import base64
import pathlib
import sys
pathlib.Path(sys.argv[1]).write_bytes(base64.b64encode(bytes(range(32))) + b"\n")
PY
done
openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$fixture/private.pem"
openssl pkcs8 -topk8 -nocrypt -in "$fixture/private.pem" -outform DER \
  | base64 -w 0 > "$secret_dir/oidc-private-key"
printf '\n' >> "$secret_dir/oidc-private-key"
openssl pkey -in "$fixture/private.pem" -pubout -outform DER \
  | base64 -w 0 > "$secret_dir/oidc-public-key"
printf '\n' >> "$secret_dir/oidc-public-key"
for name in hr-client-secret approval-client-secret admin-client-secret \
  admin-internal-api-secret turnstile-secret gmail-app-password; do
  printf 'phase8-test-only-%s\n' "$name" > "$secret_dir/$name"
done
chmod 600 "$secret_dir"/*

for service in auth-server admin-server hr-server approval-server; do
  source_ref="$base_image_prefix-$service:$base_image_tag"
  target_ref="$image_registry/$image_namespace/sso-lab-$service:$release_tag"
  docker image inspect "$source_ref" >/dev/null
  docker tag "$source_ref" "$target_ref"
  temporary_refs="$temporary_refs $target_ref"
done

cat > "$fixture/.env" <<EOF
BACKEND_RUNTIME_UID=$runtime_uid
BACKEND_RUNTIME_GID=$runtime_gid
PRODUCTION_SECRET_DIR=$secret_dir
EOF
python3 scripts/test/validate-phase8-production-secrets.py "$fixture"

compose config --quiet
compose up -d --wait --no-build \
  postgres auth-server admin-server hr-server approval-server

for service in postgres auth-server admin-server hr-server approval-server; do
  container=$(compose ps -q "$service")
  test -n "$container"
  test "$(docker inspect "$container" --format '{{.State.Health.Status}}')" = healthy
  printf 'production_backend_health|%s|pass\n' "$service"
done

for service in auth-server admin-server hr-server approval-server; do
  container=$(compose ps -q "$service")
  test "$(docker inspect "$container" --format '{{.Config.User}}')" = \
    "$runtime_uid:$runtime_gid"
done

compose logs --no-color auth-server admin-server hr-server approval-server \
  > "$fixture/backend.log"
for name in email-encryption-key email-lookup-hmac-key otp-hmac-key \
  totp-encryption-key oidc-private-key oidc-public-key hr-client-secret \
  approval-client-secret admin-client-secret admin-internal-api-secret \
  turnstile-secret gmail-app-password; do
  if grep -F -f "$secret_dir/$name" "$fixture/backend.log" >/dev/null; then
    echo "Production test Secret value was found in Backend logs: $name" >&2
    exit 1
  fi
done
if grep -Fq 'phase8-test@example.invalid' "$fixture/backend.log"; then
  echo 'Production test email value was found in Backend logs' >&2
  exit 1
fi

printf 'production_backend_runtime_identity|all_non_root|pass\n'
printf 'production_backend_secret_log_scan|pass\n'
printf 'production_backend_health|all_services|pass\n'
