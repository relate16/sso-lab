#!/usr/bin/env python3
"""Validate rendered local-shared-db Compose without printing environment values."""

from __future__ import annotations

import json
import sys


model = json.load(sys.stdin)
services = model["services"]
assert "postgres" not in services, "local postgres must be inactive in shared DB mode"

auth = services["auth-server"]
assert not auth.get("depends_on"), "auth-server must not depend on local postgres"
auth_env = auth["environment"]
assert auth_env["SPRING_PROFILES_ACTIVE"] == "local,local-shared-db"
assert auth_env["SPRING_FLYWAY_ENABLED"] == "false"
assert auth_env["BOOTSTRAP_ADMIN_ENABLED"] == "false"
assert auth_env["TURNSTILE_ENABLED"] == "false"
assert auth_env["GMAIL_SMTP_ENABLED"] == "false"
assert auth_env["SSO_TEST_SUPPORT_ENABLED"] == "false"
assert auth_env["AUTH_DB_URL"].startswith(
    "jdbc:postgresql://host.docker.internal:15432/"
)

expected = {
    "auth-server": None,
    "admin-server": ("ADMIN_REGISTRATION_ID", "admin-client"),
    "hr-server": ("HR_REGISTRATION_ID", "hr-client"),
    "approval-server": ("APPROVAL_REGISTRATION_ID", "approval-client"),
}
for service_name, registration in expected.items():
    service = services[service_name]
    assert all(port["host_ip"] == "127.0.0.1" for port in service["ports"])
    if registration:
        key, value = registration
        assert service["environment"][key] == value

assert not any(
    published.get("published") == "5432"
    for service in services.values()
    for published in service.get("ports", [])
)
print("local_shared_db_compose_validation|pass")
