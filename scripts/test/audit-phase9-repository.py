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

SERVICE_SCHEMA_OWNERS = {
    # `auth` is the current physical name of the Identity-owned schema.
    # `identity` is reserved for the same owner if a separately approved rename occurs.
    "auth-server": frozenset({"auth", "identity"}),
    "admin-server": frozenset({"admin"}),
    "hr-server": frozenset({"hr"}),
    "approval-server": frozenset({"approval"}),
}
OWNED_SCHEMA_NAMES = frozenset(
    schema
    for schemas in SERVICE_SCHEMA_OWNERS.values()
    for schema in schemas
)
SOURCE_SUFFIXES = {".java", ".kt", ".kts", ".sql", ".yml", ".yaml", ".properties", ".xml"}
CODE_SUFFIXES = {".java", ".kt", ".kts"}
CONFIG_SUFFIXES = {".yml", ".yaml", ".properties", ".xml"}
AUTH_SERVER_PACKAGE_REFERENCE = re.compile(r"\bcom\.ssolab\.auth(?:\.|;)")
AUTH_SERVER_PROJECT_REFERENCE = re.compile(r"[\"']:backend:auth-server[\"']")
AUTH_DB_CONFIGURATION_REFERENCE = re.compile(
    r"\bAUTH_DB_(?:URL|USER|USERNAME|PASSWORD)\b",
    re.IGNORECASE,
)
SCHEMA_QUALIFIED_REFERENCE = re.compile(
    rf"(?<![A-Za-z0-9_])[\"`]?({'|'.join(sorted(OWNED_SCHEMA_NAMES))})[\"`]?\s*\.",
    re.IGNORECASE,
)
SCHEMA_DDL_REFERENCE = re.compile(
    rf"\b(?:CREATE|ALTER|DROP)\s+SCHEMA\s+(?:IF\s+(?:NOT\s+)?EXISTS\s+)?"
    rf"({'|'.join(sorted(OWNED_SCHEMA_NAMES))})\b",
    re.IGNORECASE,
)
JPA_SCHEMA_REFERENCE = re.compile(
    rf"\bschema\s*=\s*[\"']({'|'.join(sorted(OWNED_SCHEMA_NAMES))})[\"']",
    re.IGNORECASE,
)
CONFIG_SCHEMA_REFERENCE = re.compile(
    rf"(?im)^\s*(?:default[-_.]?schema|schemas?|currentSchema)\s*[:=]\s*"
    rf"\[?\s*({'|'.join(sorted(OWNED_SCHEMA_NAMES))})\b",
)
SEARCH_PATH_REFERENCE = re.compile(r"(?im)^.*\bsearch_path\b.*$")
CURRENT_SCHEMA_REFERENCE = re.compile(r"(?i)\bcurrentSchema=([^\s;&\"']+)")
STRING_LITERAL = re.compile(r'"""(.*?)"""|"(?:\\.|[^"\\])*"', re.DOTALL)
URL_REFERENCE = re.compile(r"\bhttps?://[^\s\"']+", re.IGNORECASE)


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


def quoted_strings(text: str) -> list[str]:
    values: list[str] = []
    for match in STRING_LITERAL.finditer(text):
        literal = match.group(1)
        if literal is None:
            literal = match.group(0)[1:-1]
        values.append(literal)
    return values


def strip_sql_comments(text: str) -> str:
    without_blocks = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"(?m)--.*$", "", without_blocks)


def explicit_schema_references(path: pathlib.Path, text: str) -> set[str]:
    suffix = path.suffix.casefold()
    searchable: list[str] = []
    if suffix == ".sql":
        searchable.append(strip_sql_comments(text))
    elif suffix in CODE_SUFFIXES:
        searchable.extend(quoted_strings(text))
    elif suffix in CONFIG_SUFFIXES:
        searchable.append(text)

    references: set[str] = set()
    for candidate in searchable:
        candidate = URL_REFERENCE.sub("", candidate).replace(r'\"', '"')
        references.update(match.group(1).casefold() for match in SCHEMA_QUALIFIED_REFERENCE.finditer(candidate))
        references.update(match.group(1).casefold() for match in SCHEMA_DDL_REFERENCE.finditer(candidate))
        for line in SEARCH_PATH_REFERENCE.findall(candidate):
            references.update(
                schema for schema in OWNED_SCHEMA_NAMES
                if re.search(rf"(?<![A-Za-z0-9_]){re.escape(schema)}(?![A-Za-z0-9_])", line, re.IGNORECASE)
            )
        for match in CURRENT_SCHEMA_REFERENCE.finditer(candidate):
            references.update(
                schema.casefold()
                for schema in match.group(1).split(",")
                if schema.casefold() in OWNED_SCHEMA_NAMES
            )

    if suffix in CODE_SUFFIXES:
        references.update(match.group(1).casefold() for match in JPA_SCHEMA_REFERENCE.finditer(text))
    if suffix in CONFIG_SUFFIXES:
        references.update(match.group(1).casefold() for match in CONFIG_SCHEMA_REFERENCE.finditer(text))
    return references


def require_service_source_boundary(service: str, path: pathlib.Path, text: str) -> None:
    if service not in SERVICE_SCHEMA_OWNERS:
        return
    if service != "auth-server":
        if path.name == "build.gradle.kts" and AUTH_SERVER_PROJECT_REFERENCE.search(text):
            raise AssertionError(f"{service} directly depends on the auth-server implementation module")
        if path.suffix.casefold() in CODE_SUFFIXES and AUTH_SERVER_PACKAGE_REFERENCE.search(text):
            raise AssertionError(f"{service} directly references an auth-server implementation package")
        if AUTH_DB_CONFIGURATION_REFERENCE.search(text):
            raise AssertionError(f"{service} directly uses Auth Identity datasource configuration")

    foreign_schemas = explicit_schema_references(path, text) - SERVICE_SCHEMA_OWNERS[service]
    if foreign_schemas:
        names = ", ".join(sorted(foreign_schemas))
        raise AssertionError(
            f"{service} explicitly references schema owned by another service in {path.as_posix()}: {names}"
        )


def compose_service_blocks(text: str) -> dict[str, str]:
    blocks: dict[str, list[str]] = {}
    current: str | None = None
    in_services = False
    for line in text.splitlines():
        if line == "services:":
            in_services = True
            current = None
            continue
        if not in_services:
            continue
        if line and not line.startswith(" "):
            break
        service_match = re.match(r"^  ([a-z0-9][a-z0-9-]*):\s*$", line)
        if service_match:
            current = service_match.group(1)
            blocks.setdefault(current, [])
            continue
        if current is not None:
            blocks[current].append(line)
    return {service: "\n".join(lines) for service, lines in blocks.items()}


def require_architecture_boundaries() -> None:
    for service in SERVICE_SCHEMA_OWNERS:
        service_root = ROOT / "backend" / service
        build_path = service_root / "build.gradle.kts"
        require_service_source_boundary(
            service,
            build_path,
            build_path.read_text(encoding="utf-8"),
        )
        for source_area in (service_root / "src" / "main", service_root / "src" / "test"):
            if not source_area.is_dir():
                continue
            for path in source_area.rglob("*"):
                if path.is_file() and path.suffix.casefold() in SOURCE_SUFFIXES:
                    require_service_source_boundary(service, path, path.read_text(encoding="utf-8"))

    for compose_path in ROOT.glob("docker-compose*.yml"):
        for service, block in compose_service_blocks(compose_path.read_text(encoding="utf-8")).items():
            if service in SERVICE_SCHEMA_OWNERS:
                require_service_source_boundary(service, compose_path, block)

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
