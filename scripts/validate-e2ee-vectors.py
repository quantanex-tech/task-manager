#!/usr/bin/env python3
"""Validate Task Manager Phase 1 E2EE v1 JSON test vectors.

The validator intentionally uses only the Python standard library so Android,
WearOS, Go server, CI, and future client implementations can run the same
fixture checks without Docker or heavyweight tooling.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
from typing import Any

SCHEMA_VERSION = "task-manager-e2ee-vector-v1"
SUITE_ID = "TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256"
ALLOWED_ENTITY_TYPES = {"task", "project", "label", "comment", "attachment", "tombstone"}
ALLOWED_EXPECTED_RESULTS = {
    "decrypt_ok",
    "decrypt_reject_wrong_key",
    "decrypt_reject_wrong_aad",
    "decrypt_reject_tamper",
    "decrypt_reject_wrong_space",
    "decrypt_reject_wrong_entity_id",
    "decrypt_reject_wrong_entity_type",
    "decrypt_reject_wrong_version",
    "sync_reject_replay_rollback",
    "encrypt_reject_duplicate_nonce",
    "decrypt_ok_rotated_key",
    "reject_unsupported_version",
    "reject_deprecated_version",
}
REQUIRED_SCENARIOS = {
    "success",
    "wrong_key",
    "wrong_aad",
    "tamper_ciphertext",
    "wrong_space",
    "wrong_entity_id",
    "wrong_entity_type",
    "wrong_version",
    "replay_rollback_marker",
    "duplicate_nonce_prevention",
    "rotated_key",
    "unsupported_version",
    "deprecated_version",
}
HEX_RE = re.compile(r"^[0-9a-f]+$")
SHA256_HEX_RE = re.compile(r"^[0-9a-f]{64}$")
OPAQUE_ID_RE = re.compile(r"^[a-z][a-z0-9_]{2,79}$")


class ValidationResult:
    def __init__(self, errors: list[str], scenarios: list[str]) -> None:
        self.errors = errors
        self.scenarios = scenarios

    @property
    def ok(self) -> bool:
        return not self.errors


def _require(condition: bool, errors: list[str], message: str) -> None:
    if not condition:
        errors.append(message)


def _is_hex(value: Any, *, min_len: int | None = None, exact_len: int | None = None) -> bool:
    if not isinstance(value, str) or not HEX_RE.fullmatch(value):
        return False
    if exact_len is not None and len(value) != exact_len:
        return False
    if min_len is not None and len(value) < min_len:
        return False
    return len(value) % 2 == 0


def _validate_string(value: Any, pattern: re.Pattern[str], errors: list[str], field: str) -> None:
    _require(isinstance(value, str) and bool(pattern.fullmatch(value)), errors, f"{field} must match {pattern.pattern}")


def _validate_vector(vector: Any, index: int, errors: list[str]) -> str | None:
    if not isinstance(vector, dict):
        errors.append(f"vector[{index}] must be an object")
        return None

    prefix = f"vector[{index}]"
    scenario = vector.get("scenario")
    _require(vector.get("schema_version") == SCHEMA_VERSION, errors, f"{prefix}.schema_version must be {SCHEMA_VERSION}")
    _require(isinstance(scenario, str), errors, f"{prefix}.scenario must be a string")
    _require(isinstance(vector.get("description"), str) and bool(vector["description"].strip()), errors, f"{prefix}.description is required")
    _require(vector.get("expected_result") in ALLOWED_EXPECTED_RESULTS, errors, f"{prefix}.expected_result is not recognised")

    envelope = vector.get("envelope")
    if not isinstance(envelope, dict):
        errors.append(f"{prefix}.envelope must be an object")
        return scenario if isinstance(scenario, str) else None

    _require(envelope.get("protocol_version") == 1, errors, f"{prefix}.envelope.protocol_version must be 1")
    _require(envelope.get("suite_id") == SUITE_ID, errors, f"{prefix}.envelope.suite_id must be {SUITE_ID}")
    _validate_string(envelope.get("space_id"), OPAQUE_ID_RE, errors, f"{prefix}.envelope.space_id")
    _require(envelope.get("entity_type") in ALLOWED_ENTITY_TYPES, errors, f"{prefix}.envelope.entity_type is not allowed")
    _validate_string(envelope.get("entity_id"), OPAQUE_ID_RE, errors, f"{prefix}.envelope.entity_id")
    _require(isinstance(envelope.get("entity_version"), int) and envelope["entity_version"] >= 1, errors, f"{prefix}.envelope.entity_version must be >= 1")
    _require(isinstance(envelope.get("key_epoch"), int) and envelope["key_epoch"] >= 1, errors, f"{prefix}.envelope.key_epoch must be >= 1")
    _validate_string(envelope.get("content_key_id"), OPAQUE_ID_RE, errors, f"{prefix}.envelope.content_key_id")
    _require(_is_hex(envelope.get("nonce"), exact_len=48), errors, f"{prefix}.envelope.nonce must be 24 bytes lowercase hex")
    _require(_is_hex(envelope.get("ciphertext"), min_len=2), errors, f"{prefix}.envelope.ciphertext must be lowercase hex")
    _require(isinstance(envelope.get("ciphertext_sha256"), str) and bool(SHA256_HEX_RE.fullmatch(envelope["ciphertext_sha256"])), errors, f"{prefix}.envelope.ciphertext_sha256 must be 32 bytes lowercase hex")

    aad = envelope.get("aad")
    if not isinstance(aad, dict):
        errors.append(f"{prefix}.envelope.aad must be an object")
    else:
        _require(isinstance(aad.get("canonical_json_sha256"), str) and bool(SHA256_HEX_RE.fullmatch(aad["canonical_json_sha256"])), errors, f"{prefix}.envelope.aad.canonical_json_sha256 must be 32 bytes lowercase hex")

    attachment = vector.get("attachment")
    if attachment is not None:
        if not isinstance(attachment, dict):
            errors.append(f"{prefix}.attachment must be an object")
        else:
            _require(isinstance(attachment.get("chunk_index"), int) and attachment["chunk_index"] >= 0, errors, f"{prefix}.attachment.chunk_index must be >= 0")
            _require(isinstance(attachment.get("chunk_count"), int) and attachment["chunk_count"] >= 1, errors, f"{prefix}.attachment.chunk_count must be >= 1")
            _require(isinstance(attachment.get("chunk_size_bytes"), int) and attachment["chunk_size_bytes"] >= 1, errors, f"{prefix}.attachment.chunk_size_bytes must be >= 1")
            _require(isinstance(attachment.get("plaintext_size_bytes"), int) and attachment["plaintext_size_bytes"] >= 0, errors, f"{prefix}.attachment.plaintext_size_bytes must be >= 0")
            _require(isinstance(attachment.get("plaintext_sha256"), str) and bool(SHA256_HEX_RE.fullmatch(attachment["plaintext_sha256"])), errors, f"{prefix}.attachment.plaintext_sha256 must be 32 bytes lowercase hex")

    return scenario if isinstance(scenario, str) else None


def validate_vector_directory(vector_dir: pathlib.Path) -> ValidationResult:
    errors: list[str] = []
    scenarios: list[str] = []
    files = sorted(vector_dir.glob("*.json"))
    files = [path for path in files if path.name != "schema.json"]
    if not files:
        return ValidationResult([f"no vector JSON files found in {vector_dir}"], scenarios)

    nonce_keys: dict[tuple[str, int, str], str] = {}
    for file_path in files:
        try:
            data = json.loads(file_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            errors.append(f"{file_path}: invalid JSON: {exc}")
            continue
        if not isinstance(data, list):
            errors.append(f"{file_path}: top-level value must be an array")
            continue
        for index, vector in enumerate(data):
            scenario = _validate_vector(vector, index, errors)
            if scenario:
                scenarios.append(scenario)
            if isinstance(vector, dict) and isinstance(vector.get("envelope"), dict):
                envelope = vector["envelope"]
                key = (str(envelope.get("content_key_id")), int(envelope.get("key_epoch", -1)), str(envelope.get("nonce")))
                previous = nonce_keys.get(key)
                current = f"{file_path.name}[{index}]"
                if previous is not None:
                    errors.append(f"nonce {key[2]} is reused for content_key_id {key[0]} key_epoch {key[1]}")
                else:
                    nonce_keys[key] = current

    missing = sorted(REQUIRED_SCENARIOS.difference(scenarios))
    if missing:
        errors.append("missing required scenarios: " + ", ".join(missing))
    unknown = sorted(set(scenarios).difference(REQUIRED_SCENARIOS))
    if unknown:
        errors.append("unknown scenarios: " + ", ".join(unknown))
    return ValidationResult(errors, scenarios)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate Task Manager E2EE v1 JSON vectors")
    parser.add_argument("vector_dir", nargs="?", default="testdata/e2ee/v1", type=pathlib.Path)
    args = parser.parse_args(argv)
    result = validate_vector_directory(args.vector_dir)
    if result.ok:
        print(f"validated {len(result.scenarios)} E2EE v1 vectors from {args.vector_dir}")
        return 0
    for error in result.errors:
        print(f"error: {error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
