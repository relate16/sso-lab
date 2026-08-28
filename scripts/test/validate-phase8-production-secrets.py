#!/usr/bin/env python3
"""Validate production Secret metadata and formats without printing values."""

from __future__ import annotations

import base64
import binascii
from pathlib import Path
import stat
import sys


RSA_ENCRYPTION_OID_VALUE = bytes.fromhex("2a864886f70d010101")
EXPECTED_SECRETS = (
    "email-encryption-key",
    "email-lookup-hmac-key",
    "otp-hmac-key",
    "totp-encryption-key",
    "oidc-private-key",
    "oidc-public-key",
    "hr-client-secret",
    "approval-client-secret",
    "admin-client-secret",
    "admin-internal-api-secret",
    "turnstile-secret",
    "gmail-app-password",
)


class ValidationError(Exception):
    pass


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        if key in values:
            raise ValidationError(f"duplicate environment variable: {key}")
        values[key] = value
    return values


def read_tlv(data: bytes, offset: int) -> tuple[int, bytes, int]:
    if offset + 2 > len(data):
        raise ValidationError("truncated DER value")
    tag = data[offset]
    offset += 1
    length_byte = data[offset]
    offset += 1
    if length_byte & 0x80:
        count = length_byte & 0x7F
        if count == 0 or count > 4 or offset + count > len(data):
            raise ValidationError("invalid DER length")
        length = int.from_bytes(data[offset : offset + count], "big")
        offset += count
    else:
        length = length_byte
    end = offset + length
    if end > len(data):
        raise ValidationError("truncated DER payload")
    return tag, data[offset:end], end


def children(sequence: bytes) -> list[tuple[int, bytes]]:
    result: list[tuple[int, bytes]] = []
    offset = 0
    while offset < len(sequence):
        tag, value, offset = read_tlv(sequence, offset)
        result.append((tag, value))
    return result


def top_sequence(data: bytes) -> list[tuple[int, bytes]]:
    tag, value, end = read_tlv(data, 0)
    if tag != 0x30 or end != len(data):
        raise ValidationError("DER value is not one complete SEQUENCE")
    return children(value)


def positive_integer(value: bytes) -> int:
    if not value or value[0] & 0x80:
        raise ValidationError("invalid positive DER INTEGER")
    return int.from_bytes(value, "big", signed=False)


def validate_rsa_algorithm(value: bytes) -> None:
    parts = children(value)
    if not parts or parts[0] != (0x06, RSA_ENCRYPTION_OID_VALUE):
        raise ValidationError("OIDC key algorithm is not rsaEncryption")
    if len(parts) > 2 or (len(parts) == 2 and parts[1] != (0x05, b"")):
        raise ValidationError("invalid RSA AlgorithmIdentifier")


def parse_pkcs1_private(data: bytes) -> tuple[int, int]:
    parts = top_sequence(data)
    if len(parts) < 9 or any(tag != 0x02 for tag, _ in parts[:9]):
        raise ValidationError("private key payload is not PKCS#1 RSA")
    if positive_integer(parts[0][1]) not in (0, 1):
        raise ValidationError("unsupported RSA private key version")
    return positive_integer(parts[1][1]), positive_integer(parts[2][1])


def parse_pkcs8_private(data: bytes) -> tuple[int, int]:
    parts = top_sequence(data)
    if len(parts) < 3 or [tag for tag, _ in parts[:3]] != [0x02, 0x30, 0x04]:
        raise ValidationError("OIDC private key must be PKCS#8 DER, not PKCS#1 DER")
    if positive_integer(parts[0][1]) not in (0, 1):
        raise ValidationError("unsupported PKCS#8 version")
    validate_rsa_algorithm(parts[1][1])
    return parse_pkcs1_private(parts[2][1])


def parse_x509_public(data: bytes) -> tuple[int, int]:
    parts = top_sequence(data)
    if len(parts) != 2 or [tag for tag, _ in parts] != [0x30, 0x03]:
        raise ValidationError("OIDC public key must be X.509 SubjectPublicKeyInfo DER")
    validate_rsa_algorithm(parts[0][1])
    bit_string = parts[1][1]
    if not bit_string or bit_string[0] != 0:
        raise ValidationError("invalid RSA public key BIT STRING")
    public_parts = top_sequence(bit_string[1:])
    if len(public_parts) != 2 or any(tag != 0x02 for tag, _ in public_parts):
        raise ValidationError("public key payload is not PKCS#1 RSA")
    return positive_integer(public_parts[0][1]), positive_integer(public_parts[1][1])


def decode_base64(name: str, value: bytes) -> bytes:
    try:
        text = value.decode("ascii").strip()
        if not text or any(character.isspace() for character in text):
            raise ValidationError(f"{name} must be single-line Base64 ASCII")
        return base64.b64decode(text, validate=True)
    except (UnicodeDecodeError, binascii.Error) as exception:
        raise ValidationError(f"{name} is not valid Base64 ASCII") from exception


def load_secret(path: Path, expected_uid: int, expected_gid: int) -> bytes:
    metadata = path.stat()
    if not stat.S_ISREG(metadata.st_mode):
        raise ValidationError(f"{path.name} is not a regular file")
    if stat.S_IMODE(metadata.st_mode) & 0o077:
        raise ValidationError(f"{path.name} permissions expose group/other access")
    if metadata.st_uid != expected_uid or metadata.st_gid != expected_gid:
        raise ValidationError(f"{path.name} ownership does not match Backend runtime identity")
    if metadata.st_size == 0 or metadata.st_size > 64 * 1024:
        raise ValidationError(f"{path.name} is empty or too large")
    value = path.read_bytes().strip()
    if not value:
        raise ValidationError(f"{path.name} is empty")
    return value


def main() -> int:
    production_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "/opt/sso-lab").resolve()
    env = read_env(production_dir / ".env")
    try:
        runtime_uid = int(env.get("BACKEND_RUNTIME_UID", "1000"))
        runtime_gid = int(env.get("BACKEND_RUNTIME_GID", "1000"))
    except ValueError as exception:
        raise ValidationError("BACKEND_RUNTIME_UID/GID must be numeric") from exception
    if runtime_uid <= 0 or runtime_gid <= 0:
        raise ValidationError("Backend runtime identity must be non-root")

    configured_dir = env.get("PRODUCTION_SECRET_DIR", "./secrets")
    secret_dir = Path(configured_dir)
    if not secret_dir.is_absolute():
        secret_dir = production_dir / secret_dir
    secret_dir = secret_dir.resolve()
    directory_metadata = secret_dir.stat()
    if not stat.S_ISDIR(directory_metadata.st_mode):
        raise ValidationError("Production Secret path is not a directory")
    if stat.S_IMODE(directory_metadata.st_mode) & 0o077:
        raise ValidationError("Production Secret directory permissions expose group/other access")
    if directory_metadata.st_uid != runtime_uid or directory_metadata.st_gid != runtime_gid:
        raise ValidationError("Production Secret directory ownership does not match Backend runtime identity")

    values = {
        name: load_secret(secret_dir / name, runtime_uid, runtime_gid)
        for name in EXPECTED_SECRETS
    }

    for name in ("email-encryption-key", "totp-encryption-key"):
        if len(decode_base64(name, values[name])) != 32:
            raise ValidationError(f"{name} must decode to exactly 32 bytes")
    for name in ("email-lookup-hmac-key", "otp-hmac-key"):
        if len(decode_base64(name, values[name])) < 32:
            raise ValidationError(f"{name} must decode to at least 32 bytes")

    private_modulus, private_exponent = parse_pkcs8_private(
        decode_base64("oidc-private-key", values["oidc-private-key"])
    )
    public_modulus, public_exponent = parse_x509_public(
        decode_base64("oidc-public-key", values["oidc-public-key"])
    )
    if private_modulus.bit_length() < 2048:
        raise ValidationError("OIDC RSA key must be at least 2048 bits")
    if (private_modulus, private_exponent) != (public_modulus, public_exponent):
        raise ValidationError("OIDC private/public keys are not a matching pair")

    print("production_secret_metadata|all_12|pass")
    print("production_symmetric_key_formats|all_4|pass")
    print("production_oidc_private_key|base64_pkcs8_rsa_der|pass")
    print("production_oidc_public_key|base64_x509_rsa_der|pass")
    print("production_oidc_key_pair|matching_minimum_2048_bits|pass")
    print("production_secret_preflight|pass")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValidationError) as exception:
        print(f"production_secret_preflight|fail|{exception}", file=sys.stderr)
        raise SystemExit(1)
