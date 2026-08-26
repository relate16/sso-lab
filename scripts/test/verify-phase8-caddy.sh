#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../.."

dc() {
  docker compose --env-file .env.test --env-file .env.phase4.local \
    --env-file infra/test/phase8.env \
    -f docker-compose.yml -f docker-compose.infra-test.yml "$@"
}

request() {
  host=$1
  shift
  curl --silent --show-error --insecure \
    --resolve "$host:443:127.0.0.1" "$@"
}

dc config --quiet
dc exec -T caddy caddy validate --config /etc/caddy/Caddyfile

for host in auth.sso-lab.test admin.sso-lab.test hr.sso-lab.test approval.sso-lab.test; do
  redirect=$(curl --silent --show-error --output /dev/null --write-out '%{redirect_url}' \
    --resolve "$host:80:127.0.0.1" "http://$host/")
  test "$redirect" = "https://$host/"
  request "$host" --fail --output /dev/null "https://$host/"
  printf 'https_frontend|%s|pass\n' "$host"
done

discovery=$(request auth.sso-lab.test --fail \
  https://auth.sso-lab.test/.well-known/openid-configuration)
printf '%s' "$discovery" | grep -q '"issuer":"https://auth.sso-lab.test"'
printf '%s' "$discovery" | grep -q '"jwks_uri":"https://auth.sso-lab.test/oauth2/jwks"'
request auth.sso-lab.test --fail --output /dev/null \
  https://auth.sso-lab.test/oauth2/jwks
printf 'oidc_https_discovery_jwks|pass\n'

for host in auth.sso-lab.test admin.sso-lab.test hr.sso-lab.test approval.sso-lab.test; do
  health=$(request "$host" --fail "https://$host/actuator/health")
  printf '%s' "$health" | grep -q '"status":"UP"'
  printf 'backend_health_through_caddy|%s|pass\n' "$host"
done

for host in auth.sso-lab.test admin.sso-lab.test hr.sso-lab.test approval.sso-lab.test; do
  status=$(request "$host" --output /dev/null --write-out '%{http_code}' \
    "https://$host/internal/admin/v1/users")
  test "$status" = 404
done
printf 'public_internal_route_boundary|pass\n'

headers=$(request auth.sso-lab.test --dump-header - --output /dev/null \
  https://auth.sso-lab.test/actuator/health)
printf '%s' "$headers" | grep -Eqi 'Content-Security-Policy:.*frame-ancestors'
printf '%s' "$headers" | grep -Eqi 'X-Content-Type-Options:[[:space:]]*nosniff'
printf '%s' "$headers" | grep -Eqi 'Referrer-Policy:[[:space:]]*no-referrer'
if printf '%s' "$headers" | grep -Eqi '^Server:'; then
  printf 'caddy_server_header_removed|fail\n'
  exit 1
fi
printf 'security_headers_through_caddy|pass\n'

cookie_jar=$(mktemp)
cookie_headers=$(mktemp)
trap 'rm -f "$cookie_jar" "$cookie_headers"' EXIT
csrf=$(request auth.sso-lab.test --cookie-jar "$cookie_jar" \
  --dump-header "$cookie_headers" \
  https://auth.sso-lab.test/api/v1/csrf)
csrf_header=$(printf '%s' "$csrf" | sed -n 's/.*"headerName":"\([^"]*\)".*/\1/p')
csrf_token=$(printf '%s' "$csrf" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
test -n "$csrf_header"
test -n "$csrf_token"
grep -Eqi '^Set-Cookie:.*SESSION=.*Secure.*HttpOnly.*SameSite=Lax' "$cookie_headers"

i=1
while [ "$i" -le 31 ]; do
  code=$(request auth.sso-lab.test --cookie "$cookie_jar" \
    --header "$csrf_header: $csrf_token" \
    --header "X-Forwarded-For: 203.0.113.$i" \
    --header 'Forwarded: for=198.51.100.10;proto=http' \
    --header 'Content-Type: application/json' \
    --data '{}' --output /dev/null --write-out '%{http_code}' \
    https://auth.sso-lab.test/api/v1/login/start)
  if [ "$i" -le 30 ]; then
    test "$code" = 200
  else
    test "$code" = 429
  fi
  i=$((i + 1))
done
printf 'spoofed_forwarded_headers_ignored|pass\n'

second_client=$(docker run --rm --network sso-lab-test_public-network \
  curlimages/curl:8.16.0 sh -ec '
    curl -ksS --resolve auth.sso-lab.test:443:172.30.80.254 \
      -c /tmp/cookies https://auth.sso-lab.test/api/v1/csrf >/tmp/csrf
    header=$(sed -n '\''s/.*"headerName":"\([^\"]*\)".*/\1/p'\'' /tmp/csrf)
    token=$(sed -n '\''s/.*"token":"\([^\"]*\)".*/\1/p'\'' /tmp/csrf)
    curl -ksS --resolve auth.sso-lab.test:443:172.30.80.254 \
      -b /tmp/cookies -H "$header: $token" -H "Content-Type: application/json" \
      -d "{}" -o /dev/null -w "%{http_code}" \
      https://auth.sso-lab.test/api/v1/login/start')
test "$second_client" = 200
printf 'forwarded_real_client_partition|pass\n'

for service in postgres auth-server admin-server hr-server approval-server \
  auth-web admin-web hr-web approval-web; do
  container_id=$(dc ps -q "$service")
  bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container_id")
  test "$bindings" = '{}'
done

caddy_id=$(dc ps -q caddy)
caddy_bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$caddy_id")
printf '%s' "$caddy_bindings" | grep -q '"HostIp":"127.0.0.1"'
printf 'host_port_boundary|caddy_loopback_only|pass\n'

postgres_networks=$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' \
  "$(dc ps -q postgres)")
printf '%s' "$postgres_networks" | grep -q 'db-network'
if printf '%s' "$postgres_networks" | grep -q 'public-network'; then
  printf 'postgres_network_boundary|fail\n'
  exit 1
fi
caddy_networks=$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' \
  "$caddy_id")
printf '%s' "$caddy_networks" | grep -q 'public-network'
if printf '%s' "$caddy_networks" | grep -Eq 'db-network|internal-network'; then
  printf 'caddy_network_boundary|fail\n'
  exit 1
fi
printf 'docker_network_boundary|pass\n'

if dc logs --no-color 2>&1 | grep -Eai \
  'otp[=:][^<[:space:]]|totp[_ ]?secret[=:]|recovery[_ ]?code[=:]|access[_ ]?token[=:]|refresh[_ ]?token[=:]|id[_ ]?token[=:]|client[_ ]?secret[=:]|internal[_ ]?(api|service)[_ ]?secret[=:]|authorization[_ ]?code[=:]|private[_ ]?key[=:]|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}'; then
  printf 'phase8_sensitive_log_scan|fail\n'
  exit 1
fi
printf 'phase8_sensitive_log_scan|pass\n'
