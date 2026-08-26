#!/usr/bin/env sh
set -eu

production_dir=${1:-/opt/sso-lab}
env_file="$production_dir/.env"
secret_dir="$production_dir/secrets"

test -f "$env_file"

value_of() {
  awk -v wanted="$1" '
    /^[[:space:]]*#/ { next }
    index($0, "=") == 0 { next }
    {
      key = substr($0, 1, index($0, "=") - 1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
      if (key == wanted) {
        value = substr($0, index($0, "=") + 1)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
        if (value ~ /^".*"$/ || value ~ /^'"'"'.*'"'"'$/) {
          value = substr(value, 2, length(value) - 2)
        }
        print value
        exit
      }
    }
  ' "$env_file"
}

presence() {
  key=$1
  value=$(value_of "$key")
  if [ -n "$value" ] && ! printf '%s' "$value" | grep -Eqi '^(change-me|replace-me|placeholder|<.*>)$'; then
    printf 'env|%s|present\n' "$key"
  else
    printf 'env|%s|missing_or_placeholder\n' "$key"
  fi
}

exact() {
  key=$1
  expected=$2
  value=$(value_of "$key")
  if [ "$value" = "$expected" ]; then
    printf 'env|%s|exact\n' "$key"
  elif [ -z "$value" ]; then
    printf 'env|%s|missing\n' "$key"
  else
    printf 'env|%s|mismatch\n' "$key"
  fi
}

presence ACME_EMAIL
exact AUTH_HOSTNAME today-sso-auth.duckdns.org
exact ADMIN_HOSTNAME today-sso-admin.duckdns.org
exact HR_HOSTNAME today-sso-hr.duckdns.org
exact APPROVAL_HOSTNAME today-sso-approval.duckdns.org
exact AUTH_PUBLIC_URL https://today-sso-auth.duckdns.org
exact ADMIN_PUBLIC_URL https://today-sso-admin.duckdns.org
exact HR_PUBLIC_URL https://today-sso-hr.duckdns.org
exact APPROVAL_PUBLIC_URL https://today-sso-approval.duckdns.org
exact HR_REDIRECT_URI https://today-sso-hr.duckdns.org/login/oauth2/code/hr-client
exact APPROVAL_REDIRECT_URI https://today-sso-approval.duckdns.org/login/oauth2/code/approval-client
exact ADMIN_REDIRECT_URI https://today-sso-admin.duckdns.org/login/oauth2/code/admin-client
exact HR_POST_LOGOUT_REDIRECT_URI https://today-sso-hr.duckdns.org/
exact APPROVAL_POST_LOGOUT_REDIRECT_URI https://today-sso-approval.duckdns.org/
exact ADMIN_POST_LOGOUT_REDIRECT_URI https://today-sso-admin.duckdns.org/
exact TURNSTILE_ENABLED true
presence TURNSTILE_SITE_KEY
exact GMAIL_SMTP_ENABLED true
exact GMAIL_SMTP_HOST smtp.gmail.com
exact GMAIL_SMTP_PORT 587
presence GMAIL_SMTP_USERNAME
presence GMAIL_SMTP_FROM
gmail_username=$(value_of GMAIL_SMTP_USERNAME)
gmail_from=$(value_of GMAIL_SMTP_FROM)
if printf '%s' "$gmail_username" | grep -Eq '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'; then
  printf 'env|GMAIL_SMTP_USERNAME|email_format_valid\n'
else
  printf 'env|GMAIL_SMTP_USERNAME|email_format_invalid\n'
fi
if printf '%s' "$gmail_from" | grep -Eq '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'; then
  printf 'env|GMAIL_SMTP_FROM|email_format_valid\n'
else
  printf 'env|GMAIL_SMTP_FROM|email_format_invalid\n'
fi
if [ -n "$gmail_username" ] && [ "$gmail_username" = "$gmail_from" ]; then
  printf 'env|GMAIL_SMTP_USERNAME_FROM|same\n'
else
  printf 'env|GMAIL_SMTP_USERNAME_FROM|different_or_missing\n'
fi
exact AUTH_DB_URL jdbc:postgresql://postgres:5432/today_sso
exact POSTGRES_DB today_sso
exact POSTGRES_USER today_sso
presence POSTGRES_PASSWORD
exact IMAGE_REGISTRY ghcr.io
exact IMAGE_NAMESPACE relate16
exact IMAGE_TAG v1.0.0
presence BOOTSTRAP_ADMIN_ENABLED
presence BOOTSTRAP_ADMIN_EMAIL

for key in COMPOSE_PROJECT_NAME PRODUCTION_SECRET_DIR PUBLIC_NETWORK_SUBNET \
  CADDY_IPV4_ADDRESS TRUSTED_PROXY_CIDR SERVER_FORWARD_HEADERS_STRATEGY \
  SPRING_PROFILES_ACTIVE SESSION_COOKIE_SECURE AUTH_LOGIN_PAGE_URI \
  JWT_SIGNING_KEY_ID HR_CLIENT_ID APPROVAL_CLIENT_ID ADMIN_CLIENT_ID \
  ADMIN_INTERNAL_CLIENT_ID EMAIL_ENCRYPTION_KEY_VERSION TOTP_ENCRYPTION_KEY_VERSION; do
  presence "$key"
done

for name in email-encryption-key email-lookup-hmac-key otp-hmac-key \
  totp-encryption-key oidc-private-key oidc-public-key hr-client-secret \
  approval-client-secret admin-client-secret admin-internal-api-secret \
  turnstile-secret gmail-app-password; do
  path="$secret_dir/$name"
  if [ -f "$path" ]; then
    mode=$(stat -c '%a' "$path")
    owner=$(stat -c '%U:%G' "$path")
    printf 'secret|%s|present|mode=%s|owner=%s\n' "$name" "$mode" "$owner"
  else
    printf 'secret|%s|missing\n' "$name"
  fi
done

for name in jwt-private-key.pem jwt-public-key.pem; do
  path="$secret_dir/$name"
  if [ -f "$path" ]; then
    mode=$(stat -c '%a' "$path")
    owner=$(stat -c '%U:%G' "$path")
    printf 'legacy_secret|%s|present_unread|mode=%s|owner=%s\n' "$name" "$mode" "$owner"
  else
    printf 'legacy_secret|%s|absent\n' "$name"
  fi
done

for file in docker-compose.yml docker-compose.prod.yml infra/caddy/Caddyfile infra/caddy/sites.caddy; do
  path="$production_dir/$file"
  if [ -f "$path" ]; then
    size=$(stat -c '%s' "$path")
    printf 'production_file|%s|present|bytes=%s\n' "$file" "$size"
  else
    printf 'production_file|%s|missing\n' "$file"
  fi
done
