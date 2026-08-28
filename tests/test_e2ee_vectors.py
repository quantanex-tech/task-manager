import importlib.util
import hashlib
import json
import pathlib
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
VALIDATOR_PATH = ROOT / "scripts" / "validate-e2ee-vectors.py"
VECTOR_DIR = ROOT / "testdata" / "e2ee" / "v1"
VECTOR_PATH = VECTOR_DIR / "vectors.json"


def canonical_json_bytes(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def aad_document(envelope):
    return {
        "protocol_version": envelope["protocol_version"],
        "suite_id": envelope["suite_id"],
        "space_id": envelope["space_id"],
        "entity_id": envelope["entity_id"],
        "entity_version": envelope["entity_version"],
        "key_epoch": envelope["key_epoch"],
        "content_key_id": envelope["content_key_id"],
    }


def load_validator():
    spec = importlib.util.spec_from_file_location("validate_e2ee_vectors", VALIDATOR_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class E2EEVectorValidationTests(unittest.TestCase):
    def test_server_visible_envelope_has_no_protected_object_type(self):
        vectors = json.loads(VECTOR_PATH.read_text(encoding="utf-8"))
        schema = json.loads((VECTOR_DIR / "schema.json").read_text(encoding="utf-8"))

        for vector in vectors:
            self.assertNotIn("entity_type", vector["envelope"], vector["scenario"])
        self.assertNotIn("entity_type", json.dumps(schema))

    def test_repository_vectors_have_recomputed_hashes(self):
        vectors = json.loads(VECTOR_PATH.read_text(encoding="utf-8"))

        for vector in vectors:
            envelope = vector["envelope"]
            expected_aad_hash = hashlib.sha256(canonical_json_bytes(aad_document(envelope))).hexdigest()
            expected_ciphertext_hash = hashlib.sha256(bytes.fromhex(envelope["ciphertext"])).hexdigest()

            self.assertEqual(expected_aad_hash, envelope["aad"]["canonical_json_sha256"], vector["scenario"])
            self.assertEqual(expected_ciphertext_hash, envelope["ciphertext_sha256"], vector["scenario"])

    def test_version_rejection_scenarios_keep_actual_protocol_versions(self):
        vectors = {vector["scenario"]: vector for vector in json.loads(VECTOR_PATH.read_text(encoding="utf-8"))}

        self.assertGreater(vectors["unsupported_version"]["envelope"]["protocol_version"], 1)
        self.assertEqual(0, vectors["deprecated_version"]["envelope"]["protocol_version"])

    def test_repository_vectors_validate_against_stable_schema(self):
        validator = load_validator()

        result = validator.validate_vector_directory(VECTOR_DIR)

        self.assertEqual([], result.errors)
        self.assertEqual(
            {
                "success",
                "wrong_key",
                "wrong_aad",
                "tamper_ciphertext",
                "wrong_space",
                "wrong_entity_id",
                "wrong_version",
                "replay_rollback_marker",
                "duplicate_nonce_prevention",
                "rotated_key",
                "unsupported_version",
                "deprecated_version",
            },
            set(result.scenarios),
        )

    def test_duplicate_nonce_within_key_epoch_is_rejected(self):
        validator = load_validator()
        duplicate_vectors = [
            {
                "schema_version": "task-manager-e2ee-vector-v1",
                "scenario": "success",
                "description": "first envelope",
                "expected_result": "decrypt_ok",
                "envelope": {
                    "protocol_version": 1,
                    "suite_id": "TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256",
                    "space_id": "sp_test",
                    "entity_id": "ent_a",
                    "entity_version": 7,
                    "key_epoch": 3,
                    "content_key_id": "ck_test_epoch_3",
                    "nonce": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "aad": {"canonical_json_sha256": "0" * 64},
                    "ciphertext": "bbbbbbbbbbbbbbbb",
                    "ciphertext_sha256": "c" * 64,
                },
            },
            {
                "schema_version": "task-manager-e2ee-vector-v1",
                "scenario": "duplicate_nonce_prevention",
                "description": "reuses nonce with same key epoch",
                "expected_result": "encrypt_reject_duplicate_nonce",
                "envelope": {
                    "protocol_version": 1,
                    "suite_id": "TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256",
                    "space_id": "sp_test",
                    "entity_id": "ent_b",
                    "entity_version": 1,
                    "key_epoch": 3,
                    "content_key_id": "ck_test_epoch_3",
                    "nonce": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "aad": {"canonical_json_sha256": "1" * 64},
                    "ciphertext": "dddddddddddddddd",
                    "ciphertext_sha256": "e" * 64,
                },
            },
        ]
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "vectors.json"
            path.write_text(json.dumps(duplicate_vectors), encoding="utf-8")

            result = validator.validate_vector_directory(pathlib.Path(tmp))

        self.assertIn(
            "nonce aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa is reused for content_key_id ck_test_epoch_3 key_epoch 3",
            result.errors,
        )


if __name__ == "__main__":
    unittest.main()
