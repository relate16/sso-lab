#!/usr/bin/env python3
"""Fail closed on common Secret/PII and architecture-boundary regressions."""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

FORBIDDEN_PATH_PARTS = {
    "secrets", "node_modules", "build", "dist", "logs", ".gradle", ".tooling",
    ".pnpm-store", ".idea", ".vscode", "tmp", "temp",
}
FORBIDDEN_SUFFIXES = {".pem", ".key", ".p12", ".jks", ".log"}
ALLOWED_ENV_EXAMPLE_PATHS = {".env.example", ".env.frontend.local.example"}
SECRET_PATTERNS = {
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "github-token": re.compile(rb"\b(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}\b"),
    "aws-access-key": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
}
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})")
PUBLIC_IP_LITERALS = (
    b"210.121." + b"175.242",
    b"175.197." + b"31.136",
)


def candidates() -> list[pathlib.Path]:
    output = subprocess.check_output(
        ["git", "-c", f"safe.directory={ROOT.as_posix()}", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT,
        text=True,
        encoding="utf-8",
    )
    return [ROOT / line for line in output.splitlines() if line]


def require_safe_path(path: pathlib.Path) -> None:
    relative = path.relative_to(ROOT)
    parts = set(relative.parts)
    if parts & FORBIDDEN_PATH_PARTS:
        raise AssertionError(f"forbidden generated/secret path is a Git candidate: {relative}")
    if relative.name.startswith(".env") and relative.as_posix() not in ALLOWED_ENV_EXAMPLE_PATHS:
        raise AssertionError(f"runtime environment file is a Git candidate: {relative}")
    if relative.suffix.casefold() in FORBIDDEN_SUFFIXES:
        raise AssertionError(f"credential/log file is a Git candidate: {relative}")


def audit_content(path: pathlib.Path) -> None:
    if not path.is_file():
        return
    data = path.read_bytes()
    if b"\0" in data:
        return
    relative = path.relative_to(ROOT)
    for label, pattern in SECRET_PATTERNS.items():
        if pattern.search(data):
            raise AssertionError(f"{label} shaped value in {relative}")
    for ip in PUBLIC_IP_LITERALS:
        if ip in data:
            raise AssertionError(f"obsolete public IPv4 literal in {relative}")
    text = data.decode("utf-8")
    for match in EMAIL.finditer(text):
        domain = match.group(1).casefold()
        if domain not in {"example.com", "example.test", "example.invalid"}:
            raise AssertionError(f"non-example email/PII in {relative}")


def require_architecture_boundaries() -> None:
    for service in ("admin-server", "hr-server", "approval-server"):
        service_root = ROOT / "backend" / service
        build_text = (service_root / "build.gradle.kts").read_text(encoding="utf-8").casefold()
        for dependency in ("postgresql", "jdbc", "jpa", "flyway"):
            if dependency in build_text:
                raise AssertionError(f"{service} directly declares Identity DB dependency {dependency}")
        java = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (service_root / "src").rglob("*.java")
        )
        if re.search(r"@(Entity|Repository)\b|JpaRepository|JdbcTemplate", java):
            raise AssertionError(f"{service} contains direct Identity persistence code")

    frontend_source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "frontend").rglob("*.ts*")
        if "node_modules" not in path.parts and "dist" not in path.parts
    )
    if re.search(r"\b(?:localStorage|sessionStorage|indexedDB)\b", frontend_source):
        raise AssertionError("Browser token-capable storage API is present in frontend source")

    compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
    if re.search(r"(?:5432|8080|2019):(?:5432|8080|2019)", compose):
        raise AssertionError("base Compose publishes a Backend/DB/Caddy Admin port")


def require_example_env_placeholders() -> None:
    sensitive = {
        "POSTGRES_PASSWORD", "SSO_JWT_PRIVATE_KEY", "SSO_EMAIL_ENCRYPTION_KEY",
        "SSO_EMAIL_LOOKUP_HMAC_KEY", "SSO_OTP_HMAC_KEY", "SSO_TOTP_ENCRYPTION_KEY",
        "HR_CLIENT_SECRET", "APPROVAL_CLIENT_SECRET", "ADMIN_CLIENT_SECRET",
        "ADMIN_INTERNAL_API_SECRET", "TURNSTILE_SECRET_KEY", "GMAIL_APP_PASSWORD",
    }
    values: dict[str, str] = {}
    for line in (ROOT / ".env.example").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key] = value
    for key in sensitive:
        value = values.get(key)
        if value is None:
            raise AssertionError(f".env.example is missing {key}")
        if value and not value.startswith("CHANGE_ME"):
            raise AssertionError(f".env.example contains a non-placeholder value for {key}")


def main() -> int:
    paths = candidates()
    for path in paths:
        require_safe_path(path)
        audit_content(path)
    require_architecture_boundaries()
    require_example_env_placeholders()
    print(f"phase9_repository_audit|candidates={len(paths)}|pass")
    print("phase9_secret_pii_audit|pass")
    print("phase9_service_browser_boundary_audit|pass")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeError, subprocess.CalledProcessError) as error:
        print(f"phase9_repository_audit|fail|{error}", file=sys.stderr)
        raise SystemExit(1)
