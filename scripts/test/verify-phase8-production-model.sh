#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../.."

config_file=$(mktemp)
trap 'rm -f "$config_file"' EXIT INT TERM

IMAGE_REGISTRY=ghcr.io \
IMAGE_NAMESPACE=relate16 \
IMAGE_TAG=v1.0.0 \
docker compose --env-file infra/test/phase8-prod-config.env \
  -f docker-compose.yml -f docker-compose.prod.yml \
  config --format json >"$config_file"

python3 - "$config_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    model = json.load(handle)

services = model["services"]
application_services = (
    "auth-server", "admin-server", "hr-server", "approval-server",
    "auth-web", "admin-web", "hr-web", "approval-web",
)

for service in application_services:
    expected = f"ghcr.io/relate16/sso-lab-{service}:v1.0.0"
    assert services[service]["image"] == expected, (service, services[service]["image"])

for service in ("auth-server", "admin-server", "hr-server", "approval-server"):
    assert services[service].get("user") == "1000:1000", service

for service in ("postgres",) + application_services:
    assert not services[service].get("ports"), service

caddy_ports = {
    (int(port["target"]), int(port["published"]), port.get("protocol", "tcp"))
    for port in services["caddy"].get("ports", [])
}
assert caddy_ports == {(80, 80, "tcp"), (443, 443, "tcp"), (443, 443, "udp")}
assert all(target != 2019 for target, _, _ in caddy_ports)

postgres_mounts = services["postgres"].get("volumes", [])
assert any(
    mount.get("source") == "postgres-data"
    and mount.get("target") == "/var/lib/postgresql/data"
    for mount in postgres_mounts
)

networks = model["networks"]
assert networks["db-network"].get("internal") is True
assert networks["internal-network"].get("internal") is True
assert not networks["public-network"].get("internal", False)

def memberships(service):
    value = services[service].get("networks", {})
    return set(value if isinstance(value, dict) else value)

assert memberships("postgres") == {"db-network"}
assert memberships("auth-server") == {"db-network", "internal-network", "public-network"}
assert "db-network" not in memberships("admin-server")
assert "db-network" not in memberships("hr-server")
assert "db-network" not in memberships("approval-server")
assert memberships("caddy") == {"public-network"}

auth_web_environment = services["auth-web"].get("environment", {})
assert auth_web_environment.get("TURNSTILE_ENABLED") == "true"
assert auth_web_environment.get("TURNSTILE_SITE_KEY") == "1x00000000000000000000AA"
assert not services["auth-web"].get("secrets")

def secret_sources(service):
    result = set()
    for item in services[service].get("secrets", []):
        result.add(item if isinstance(item, str) else item.get("source"))
    return result

auth_secrets = secret_sources("auth-server")
assert "turnstile-secret" in auth_secrets
assert "gmail-app-password" in auth_secrets
for service in application_services[1:]:
    assert "turnstile-secret" not in secret_sources(service)

expected_bff_secret_targets = {
    "admin-server": {
        "admin-client-secret": "ADMIN_CLIENT_SECRET",
        "admin-internal-api-secret": "ADMIN_INTERNAL_API_SECRET",
    },
    "hr-server": {"hr-client-secret": "HR_CLIENT_SECRET"},
    "approval-server": {"approval-client-secret": "APPROVAL_CLIENT_SECRET"},
}
for service, expected_targets in expected_bff_secret_targets.items():
    actual_targets = {
        item["source"]: item.get("target", item["source"])
        for item in services[service].get("secrets", [])
    }
    assert actual_targets == expected_targets, (service, actual_targets)

expected_secret_files = {
    name: f"/tmp/sso-lab-phase8-secrets/{name}"
    for name in (
        "email-encryption-key", "email-lookup-hmac-key", "otp-hmac-key",
        "totp-encryption-key", "oidc-private-key", "oidc-public-key",
        "hr-client-secret", "approval-client-secret", "admin-client-secret",
        "admin-internal-api-secret", "turnstile-secret", "gmail-app-password",
    )
}
actual_secret_files = {
    name: definition.get("file") for name, definition in model.get("secrets", {}).items()
}
assert actual_secret_files == expected_secret_files, actual_secret_files

auth_environment = services["auth-server"].get("environment", {})
expected_urls = {
    "AUTH_PUBLIC_URL": "https://auth.example.invalid",
    "HR_REDIRECT_URI": "https://hr.example.invalid/login/oauth2/code/hr-client",
    "APPROVAL_REDIRECT_URI": "https://approval.example.invalid/login/oauth2/code/approval-client",
    "ADMIN_REDIRECT_URI": "https://admin.example.invalid/login/oauth2/code/admin-client",
    "HR_POST_LOGOUT_REDIRECT_URI": "https://hr.example.invalid/",
    "APPROVAL_POST_LOGOUT_REDIRECT_URI": "https://approval.example.invalid/",
    "ADMIN_POST_LOGOUT_REDIRECT_URI": "https://admin.example.invalid/",
}
for key, expected in expected_urls.items():
    actual = auth_environment.get(key)
    assert actual == expected, (key, actual)
    assert "*" not in actual
    assert actual.startswith("https://")

print("production_compose_render|pass")
print("production_release_images|pass")
print("production_port_boundary|pass")
print("production_network_boundary|pass")
print("production_postgres_volume_contract|pass")
print("production_turnstile_secret_boundary|pass")
print("production_backend_secret_runtime_identity|pass")
print("production_bff_secret_mount_targets|pass")
print("production_exact_https_oidc_urls|pass")
PY

grep -Fq 'respond @internal 404' infra/caddy/sites.caddy
grep -Fq 'header_up -Forwarded' infra/caddy/sites.caddy
grep -Fq 'header_up -X-Real-IP' infra/caddy/sites.caddy
grep -Fq 'admin 127.0.0.1:2019' infra/caddy/Caddyfile
printf 'production_caddy_static_policy|pass\n'
