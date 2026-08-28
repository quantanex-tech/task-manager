import importlib.util
import json
import pathlib
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
VALIDATOR_PATH = ROOT / "scripts" / "validate-e2ee-vectors.py"
VECTOR_DIR = ROOT / "testdata" / "e2ee" / "v1"


def load_validator():
    spec = importlib.util.spec_from_file_location("validate_e2ee_vectors", VALIDATOR_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class E2EEVectorValidationTests(unittest.TestCase):
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
                "wrong_entity_type",
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
                    "entity_type": "task",
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
                    "entity_type": "comment",
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
