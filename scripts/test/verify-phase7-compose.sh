#!/usr/bin/env sh
set -eu

COMPOSE="docker compose --env-file .env.test --env-file .env.phase4.local"

bash scripts/test/verify-phase6-compose.sh

for service in auth-server admin-server hr-server approval-server; do
  headers=$(docker exec "sso-lab-test-${service}-1" \
    wget -qSO /dev/null http://127.0.0.1:8080/actuator/health 2>&1)
  printf '%s' "$headers" | grep -Eqi 'Content-Security-Policy:.*frame-ancestors'
  printf '%s' "$headers" | grep -Eqi 'X-Content-Type-Options:[[:space:]]*nosniff'
  printf '%s' "$headers" | grep -Eqi 'X-Frame-Options:[[:space:]]*DENY'
  printf '%s' "$headers" | grep -Eqi 'Referrer-Policy:[[:space:]]*no-referrer'
  printf 'backend_security_headers|%s|pass\n' "$service"
done

auth_web_headers=$(docker exec sso-lab-test-auth-web-1 \
  wget -qSO /dev/null http://127.0.0.1:8080/ 2>&1)
printf '%s' "$auth_web_headers" | grep -Eqi \
  'Content-Security-Policy:.*challenges.cloudflare.com.*frame-ancestors'
printf '%s' "$auth_web_headers" | grep -Eqi 'X-Frame-Options:[[:space:]]*DENY'
printf 'auth_web_turnstile_csp|pass\n'

csrf_status=$(docker exec sso-lab-test-auth-server-1 sh -c \
  "wget -qO /dev/null --server-response --post-data='{}' \
  --header='Content-Type: application/json' \
  http://127.0.0.1:8080/api/v1/login/start 2>&1 || true")
printf '%s' "$csrf_status" | grep -Eq 'HTTP/[0-9.]+ 403'
printf 'anonymous_mutation_csrf|pass\n'

docker exec sso-lab-test-auth-server-1 sh -c \
  'test "${TURNSTILE_ENABLED:-}" = "false"'
grep -Eq 'enabled:[[:space:]]+\$\{TURNSTILE_ENABLED:true\}' \
  backend/auth-server/src/main/resources/application-prod.yml
printf 'turnstile_test_disabled_prod_default_enabled|pass\n'

if $COMPOSE logs --no-color 2>&1 | grep -Eai \
  'otp[=:][^<[:space:]]|totp[_ ]?secret[=:]|recovery[_ ]?code[=:]|access[_ ]?token[=:]|refresh[_ ]?token[=:]|id[_ ]?token[=:]|logout[_ ]?token[=:]|client[_ ]?secret[=:]|internal[_ ]?(api|service)[_ ]?secret[=:]|authorization[_ ]?code[=:]|private[_ ]?key[=:]|reauth[_ ]?proof[=:]|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}'; then
  printf 'phase7_sensitive_log_scan|fail\n'
  exit 1
fi
printf 'phase7_sensitive_log_scan|pass\n'

if grep -R -E 'localStorage|sessionStorage|indexedDB' frontend/*/src \
  --exclude-dir=node_modules --exclude-dir=dist >/dev/null 2>&1; then
  printf 'browser_token_storage|fail\n'
  exit 1
fi
printf 'browser_token_storage|pass\n'

if grep -E 'spring-data-jpa|spring-jdbc|postgresql|flyway' \
  backend/admin-server/build.gradle.kts backend/hr-server/build.gradle.kts \
  backend/approval-server/build.gradle.kts >/dev/null 2>&1; then
  printf 'identity_db_dependency_boundary|fail\n'
  exit 1
fi
if find backend/admin-server/src backend/hr-server/src backend/approval-server/src \
  -type f \( -name '*Entity.java' -o -name '*Repository.java' \) | grep -q .; then
  printf 'identity_db_model_boundary|fail\n'
  exit 1
fi
printf 'identity_db_boundary|pass\n'
