#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../.."

project="sso-lab-phase8-secret-mount-${GITHUB_RUN_ID:-local}-$$"
secret_dir=$(mktemp -d)
runtime_uid=$(id -u)
runtime_gid=$(id -g)
base_image_prefix=${PHASE8_BASE_IMAGE_PREFIX:-sso-lab-phase8-validation}
base_image_tag=${PHASE8_BASE_IMAGE_TAG:-latest}
release_tag=phase8-secret-mount-test
image_registry=phase8.local
image_namespace=sso-lab
backend_services="auth-server admin-server hr-server approval-server"
temporary_refs=""
temporary_containers=""

compose() {
  BACKEND_RUNTIME_UID="$runtime_uid" \
  BACKEND_RUNTIME_GID="$runtime_gid" \
  PRODUCTION_SECRET_DIR="$secret_dir" \
  IMAGE_REGISTRY="$image_registry" \
  IMAGE_NAMESPACE="$image_namespace" \
  IMAGE_TAG="$release_tag" \
  docker compose --project-name "$project" \
    --env-file infra/test/phase8-prod-config.env \
    -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

cleanup() {
  set +e
  for container in $temporary_containers; do
    docker rm -f "$container" >/dev/null 2>&1
  done
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
  case "$secret_dir" in
    /tmp/*) rm -r -- "$secret_dir" ;;
  esac
}
trap cleanup EXIT INT TERM

for name in email-encryption-key email-lookup-hmac-key otp-hmac-key \
  totp-encryption-key oidc-private-key oidc-public-key hr-client-secret \
  approval-client-secret admin-client-secret admin-internal-api-secret \
  turnstile-secret gmail-app-password; do
  install -m 600 /dev/null "$secret_dir/$name"
  printf 'phase8-test-only\n' > "$secret_dir/$name"
done

for service in $backend_services; do
  source_ref="$base_image_prefix-$service:$base_image_tag"
  target_ref="$image_registry/$image_namespace/sso-lab-$service:$release_tag"
  docker image inspect "$source_ref" >/dev/null
  docker tag "$source_ref" "$target_ref"
  temporary_refs="$temporary_refs $target_ref"
done

assert_mount() {
  container=$1
  source_name=$2
  target_path=$3
  docker inspect "$container" \
    --format '{{range .Mounts}}{{printf "%s|%s|%t\n" .Source .Destination .RW}}{{end}}' \
    | grep -Fqx "$secret_dir/$source_name|$target_path|false"
  docker exec "$container" test -r "$target_path"
}

for service in $backend_services; do
  container="$project-$service"
  temporary_containers="$temporary_containers $container"
  compose run -d --no-deps --name "$container" --entrypoint sleep "$service" 120 \
    >/dev/null
  test "$(docker inspect "$container" --format '{{.Config.User}}')" = \
    "$runtime_uid:$runtime_gid"
  test "$(docker exec "$container" id -u)" = "$runtime_uid"
  test "$(docker exec "$container" id -g)" = "$runtime_gid"

  case "$service" in
    auth-server)
      for name in email-encryption-key email-lookup-hmac-key otp-hmac-key \
        totp-encryption-key oidc-private-key oidc-public-key hr-client-secret \
        approval-client-secret admin-client-secret admin-internal-api-secret \
        turnstile-secret gmail-app-password; do
        assert_mount "$container" "$name" "/run/secrets/$name"
      done
      ;;
    admin-server)
      assert_mount "$container" admin-client-secret \
        /run/secrets/ADMIN_CLIENT_SECRET
      assert_mount "$container" admin-internal-api-secret \
        /run/secrets/ADMIN_INTERNAL_API_SECRET
      ;;
    hr-server)
      assert_mount "$container" hr-client-secret /run/secrets/HR_CLIENT_SECRET
      ;;
    approval-server)
      assert_mount "$container" approval-client-secret \
        /run/secrets/APPROVAL_CLIENT_SECRET
      ;;
  esac
  printf 'production_secret_mount|%s|readable_non_root|pass\n' "$service"
done

printf 'production_secret_mounts|all_backend_services|pass\n'
