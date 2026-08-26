#!/bin/sh
set -eu

cd "$(dirname "$0")/../.."

dc() {
  docker compose --env-file .env.test --env-file .env.phase4.local "$@"
}

dc config --quiet

db_user=$(dc exec -T postgres printenv POSTGRES_USER)
db_name=$(dc exec -T postgres printenv POSTGRES_DB)
dc exec -T postgres psql -U "$db_user" -d "$db_name" -Atc \
  "SELECT version || '|' || description || '|' || success
     FROM auth.flyway_schema_history ORDER BY installed_rank;
   SELECT 'clients|' || count(*) || '|bcrypt|' || bool_and(client_secret LIKE '{bcrypt}%')
     FROM auth.oauth2_registered_client;
   SELECT 'client|' || client_id FROM auth.oauth2_registered_client ORDER BY client_id;
   SELECT 'wildcard_redirects|' || count(*)
     FROM auth.oauth2_registered_client WHERE redirect_uris LIKE '%*%';"

dc exec -T auth-server sh -c \
  "wget -qO- http://127.0.0.1:8080/.well-known/openid-configuration | grep -q authorization_endpoint
   wget -qO- http://127.0.0.1:8080/oauth2/jwks | grep -q RS256"
printf 'discovery_jwks|pass\n'

for service in postgres auth-server admin-server hr-server approval-server \
  auth-web admin-web hr-web approval-web; do
  container_id=$(dc ps -q "$service")
  container_name=$(docker inspect --format '{{.Name}}' "$container_id" | sed 's#^/##')
  health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
  port_bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container_id")
  networks=$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}},{{end}}' "$container_id")
  printf 'container|%s|health=%s|ports=%s|networks=%s\n' \
    "$container_name" "$health" "$port_bindings" "$networks"
  test "$health" = healthy
  test "$port_bindings" = '{}'
done

if dc logs --no-color 2>&1 | grep -Eqi \
  'access[_ ]?token=|refresh[_ ]?token=|id[_ ]?token=|client[_ ]?secret=|authorization[_ ]?code=|otp='; then
  printf 'sensitive_log_scan|fail\n'
  exit 1
fi
printf 'sensitive_log_scan|pass\n'
