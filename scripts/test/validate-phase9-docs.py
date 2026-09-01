#!/usr/bin/env python3
"""Validate Phase 9 required documentation without reading runtime secrets."""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

REQUIRED_MARKERS: dict[str, tuple[str, ...]] = {
    "README.md": (
        "Architecture", "SSO Demo", "Passwordless", "strict MFA",
        "Security Highlights", "Local Setup", "Future Work",
        "Screenshot / GIF", "Repository 구조",
    ),
    "DEVELOPER_SETUP.txt": ("Java 21", "Node.js 24", "Docker", "OpenAPI"),
    "docs/ARCHITECTURE.md": (
        "DB", "OIDC", "BFF", "React", "Admin Server", "Redis",
    ),
    "docs/LOCAL_SETUP.md": (
        "Java", "Node.js", "Docker", "Flyway", "Bootstrap Admin", "TOTP", "SSO",
    ),
    "docs/DEPLOYMENT.md": (
        "Caddy", "TLS", "GitHub", "Rollback", "Health", "CGNAT",
    ),
    "docs/SECURITY.md": (
        "Threat model", "CSRF", "AES-256-GCM", "Rate Limit", "Turnstile", "Audit",
    ),
    "docs/SSO_FLOW.md": (
        "Email OTP", "TOTP", "HR에서 Approval", "RP-Initiated", "Back-Channel",
        "Global Logout", "Admin Login", "Re-auth",
    ),
    "docs/TEST_GUIDE.md": (
        "Unit", "Integration", "Testcontainers", "Browser E2E", "Test Mail Sink",
        "TOTP Test Clock", "GitHub Actions",
    ),
    "docs/DOMAIN_CHANGE_GUIDE.md": ("DNS", "Caddy", "issuer", "redirect", "CORS"),
    "docs/GMAIL_SMTP_SETUP.md": ("App Password", "GMAIL_SMTP_FROM", "Local/Test"),
    "docs/TURNSTILE_SETUP.md": ("Site Key", "Secret Key", "Local", "Production"),
    "docs/BOOTSTRAP_ADMIN_GUIDE.md": (
        "BOOTSTRAP_ADMIN_ENABLED", "BOOTSTRAP_ADMIN_EMAIL", "마지막 ACTIVE ADMIN",
    ),
    "docs/SECRET_MANAGEMENT.md": (
        "SecretProvider", "Environment", "Docker Secret", "AWS SSM", "system_config",
    ),
    "docs/VM_SETUP_GUIDE.md": (
        "Bridged", "NAT", "Docker", "Router", "Let's Encrypt", "Flyway", "CGNAT",
        "Backup", "Rollback",
    ),
    "docs/AWS_SETUP_GUIDE.md": (
        "EC2", "EBS", "Elastic IP", "Security Group", "SecureString", "IAM",
        "AwsSsmSecretProvider", "RDS", "비용", "Rollback",
    ),
}

FORBIDDEN_VALUE_PATTERNS = (
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"\bghp_[A-Za-z0-9]{20,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
)


def main() -> int:
    for relative, markers in REQUIRED_MARKERS.items():
        path = ROOT / relative
        if not path.is_file():
            raise AssertionError(f"missing required document: {relative}")
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker.casefold() not in text.casefold():
                raise AssertionError(f"{relative}: missing required topic {marker}")
        for pattern in FORBIDDEN_VALUE_PATTERNS:
            if pattern.search(text):
                raise AssertionError(f"{relative}: secret-shaped content is forbidden")
        print(f"phase9_document|{relative}|pass")

    if "현재 구현 범위는 **Phase 1" in (ROOT / "README.md").read_text(encoding="utf-8"):
        raise AssertionError("README.md still describes the repository as Phase 1 only")

    print("phase9_documents|required_set|pass")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeError) as error:
        print(f"phase9_documents|fail|{error}", file=sys.stderr)
        raise SystemExit(1)
