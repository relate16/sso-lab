#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../.."

fixture=$(mktemp -d)
cleanup() {
  case "$fixture" in
    /tmp/*) rm -r -- "$fixture" ;;
  esac
}
trap cleanup EXIT INT TERM

runtime_uid=$(id -u)
runtime_gid=$(id -g)
secret_dir="$fixture/secrets"
install -d -m 700 "$secret_dir"
cat > "$fixture/.env" <<EOF
BACKEND_RUNTIME_UID=$runtime_uid
BACKEND_RUNTIME_GID=$runtime_gid
PRODUCTION_SECRET_DIR=$secret_dir
EOF

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

python3 scripts/test/validate-phase8-production-secrets.py "$fixture"

openssl rsa -traditional -in "$fixture/private.pem" -outform DER 2>/dev/null \
  | base64 -w 0 > "$secret_dir/oidc-private-key"
printf '\n' >> "$secret_dir/oidc-private-key"
chmod 600 "$secret_dir/oidc-private-key"
if python3 scripts/test/validate-phase8-production-secrets.py "$fixture" \
  > "$fixture/invalid.out" 2> "$fixture/invalid.err"; then
  echo 'PKCS#1 private key was not rejected' >&2
  exit 1
fi
grep -Fq 'must be PKCS#8 DER, not PKCS#1 DER' "$fixture/invalid.err"
printf 'production_secret_preflight_rejects_pkcs1|pass\n'
