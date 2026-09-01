#!/usr/bin/env python3
"""Validate checked-in OpenAPI contracts without loading application secrets."""

from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "docs" / "openapi"

REQUIRED: dict[str, dict[str, set[str]]] = {
    "auth-public.json": {
        "/api/v1/me/profile": {"get"},
        "/api/v1/me/profile/username": {"patch"},
        "/api/v1/me/email-change/start": {"post"},
        "/api/v1/me/email-change/{challengeId}/resend": {"post"},
        "/api/v1/me/email-change/verify": {"post"},
        "/api/v1/me/reauth/email/start": {"post"},
        "/api/v1/me/reauth/verify": {"post"},
        "/api/v1/me/account": {"delete"},
        "/api/v1/me/sessions": {"get"},
        "/api/v1/me/logout-all": {"post"},
        "/oauth2/authorize": {"get"},
        "/oauth2/token": {"post"},
        "/oauth2/jwks": {"get"},
        "/userinfo": {"get"},
        "/connect/logout": {"get"},
    },
    "auth-internal.json": {
        "/internal/admin/v1/users": {"get"},
        "/internal/admin/v1/users/{userId}/suspend": {"post"},
        "/internal/admin/v1/users/{userId}/email/reveal": {"post"},
        "/internal/admin/v1/groups": {"get", "post"},
        "/internal/admin/v1/audit-logs": {"get"},
    },
    "admin-server.json": {
        "/api/v1/session": {"get"},
        "/api/v1/admin/users": {"get"},
        "/api/v1/admin/users/{userId}/suspend": {"post"},
        "/api/v1/admin/users/{userId}/email/reveal": {"post"},
        "/api/v1/admin/groups": {"get", "post"},
        "/api/v1/admin/audit-logs": {"get"},
        "/api/v1/admin/reauth/verify": {"post"},
    },
    "hr-server.json": {"/api/v1/session": {"get"}, "/api/v1/logout": {"post"}},
    "approval-server.json": {
        "/api/v1/session": {"get"},
        "/api/v1/logout": {"post"},
    },
}


def fail(message: str) -> None:
    raise AssertionError(message)


def validate(name: str, required: dict[str, set[str]]) -> None:
    document = json.loads((OPENAPI / name).read_text(encoding="utf-8"))
    if document.get("openapi") != "3.1.0":
        fail(f"{name}: OpenAPI 3.1.0 is required")
    if not document.get("info", {}).get("title") or not document.get("info", {}).get("version"):
        fail(f"{name}: info.title and info.version are required")
    if "*" in json.dumps(document, ensure_ascii=False):
        fail(f"{name}: wildcard values are forbidden in exact contracts")
    for server in document.get("servers", []):
        if name != "auth-internal.json" and not server.get("url", "").startswith("https://"):
            fail(f"{name}: public server URL must use HTTPS")
    paths = document.get("paths", {})
    for route, methods in required.items():
        if route not in paths:
            fail(f"{name}: missing path {route}")
        for method in methods:
            operation = paths[route].get(method)
            if operation is None:
                fail(f"{name}: missing {method.upper()} {route}")
            if not operation.get("responses"):
                fail(f"{name}: {method.upper()} {route} has no responses")
    for route, item in paths.items():
        for method, operation in item.items():
            if method not in {"parameters", "summary", "description"} and not operation.get("responses"):
                fail(f"{name}: {method.upper()} {route} has no responses")


def main() -> int:
    for filename, required in REQUIRED.items():
        validate(filename, required)
        print(f"openapi_contract|{filename}|pass")
    print("openapi_contracts|all_backends|pass")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, OSError) as error:
        print(f"openapi_contracts|fail|{error}", file=sys.stderr)
        raise SystemExit(1)
