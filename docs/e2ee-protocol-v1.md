# Task Manager E2EE protocol v1

Status: Proposed protocol specification for ADR-0004
Scope: documentation and deterministic synthetic vectors only; no production crypto implementation in this slice.

## 1. Normative inputs

- ADR-0001: E2EE is mandatory before any usable release stores or syncs real task data.
- ADR-0002: Android and WearOS local persistence uses encrypted SQLite and must not leak protected content to plaintext side channels.
- ADR-0003: Phase 1 privacy threat model and server-visible metadata boundary.

Protocol v1 must not expose more plaintext metadata than ADR-0003 allows. If an implementation wants a server-visible field not listed here or in ADR-0003, it must stop and get a new accepted decision.

## 2. Primitive suite

Suite ID: `TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256`

- AEAD: XChaCha20-Poly1305, 32-byte keys, 24-byte nonces, 16-byte authentication tags.
- KDF: HKDF-SHA-256 with explicit salt and info strings.
- Hash: SHA-256 for canonical AAD fingerprints, ciphertext integrity indexing and attachment manifests.
- Serialization: RFC 8785 JSON Canonicalization Scheme (JCS), UTF-8, no comments, no NaN/Infinity, no implementation-defined map order.
- Randomness: platform CSPRNG only.

No implementation may replace any primitive with a custom algorithm or a similar-looking mode. Unsupported library availability is a blocker, not permission to improvise.

## 3. Cross-platform library strategy

Android and WearOS:

- Use Google Tink's audited Java/Kotlin AEAD support for XChaCha20-Poly1305.
- Use Android Keystore only to protect local database/device wrapping keys, not to derive server-visible content keys.
- Use an audited RFC 8785/JCS implementation or an in-repo conformance implementation with vector tests before enabling sync.
- Do not persist plaintext protected fields in SharedPreferences, logs, crash breadcrumbs, notification caches, analytics or unencrypted files.

Go server and contract tests:

- Use `golang.org/x/crypto/chacha20poly1305` for XChaCha20-Poly1305.
- Use Go's standard/x crypto HKDF-SHA-256 and `crypto/sha256`.
- Use a reviewed JCS implementation or contract tests that assert canonical byte output against committed fixtures.
- Server code validates envelope shape, authorisation, quotas, storage hashes and sequence ordering only; it never decrypts.

Future clients:

- Must validate the committed v1 vectors.
- Must not reinterpret canonical JSON, ID encoding, nonce length, version IDs or fail-closed error categories.

## 4. Identifiers and visible metadata

Opaque IDs are lower-case ASCII strings matching `^[a-z][a-z0-9_]{2,79}$` in committed vectors. Production implementations may use UUID/ULID encodings if they remain opaque and do not encode protected semantics.

Server-visible fields in v1 envelopes:

- `protocol_version`
- `suite_id`
- `space_id`
- `entity_id`
- `entity_version`
- `key_epoch`
- `content_key_id`
- `nonce`
- `aad.canonical_json_sha256`
- `ciphertext`
- `ciphertext_sha256`
- opaque attachment chunk routing fields outside the entity envelope when blob storage is required
- server-assigned sequence, storage location, length, created/updated/deleted timestamps and operational audit IDs outside the cryptographic envelope

The envelope does not expose plaintext object type. ADR-0003 forbids plaintext task/list/project/label/comment/reminder semantics, object type, priority, due-date presence, completion state, reminder existence, names, comments, recurrence expressions, search terms, attachment names/previews and decrypted content.

## 5. Key hierarchy

### 5.1 Root material

This slice does not define device provisioning, recovery or key backup. Those are separate gates. It assumes an already-authorised client holds a space content key for the current epoch.

### 5.2 Space content keys

Each space has a random 32-byte content key per epoch:

- `space_content_key[space_id, key_epoch]`
- `content_key_id` is an opaque identifier for that epoch's key.
- New normal entities in the space use the latest active epoch.
- Old epochs remain available only to authorised clients that still need to read or migrate old envelopes.

HKDF derivation for entity AEAD keys:

- Input key material: `space_content_key`.
- Salt: `sha256("task-manager:e2ee:v1:space:" || space_id || ":epoch:" || decimal(key_epoch))`.
- Info: `"task-manager:e2ee:v1:entity-aead:" || suite_id`.
- Output length: 32 bytes.

### 5.3 Attachment keys

Attachments use a random 32-byte attachment data key per attachment object. The attachment data key is wrapped by the active space content key using XChaCha20-Poly1305 with AAD binding the attachment ID, key epoch and wrapping purpose.

Rationale: attachments are large, chunked, resumable and more likely to need independent retention or future selective sharing. Normal small encrypted objects do not get per-object data keys in v1 because that would add wrapping metadata and write amplification without a Phase 1 selective-sharing requirement.

## 6. Nonce allocation

- Every AEAD encryption uses a fresh 24-byte nonce from the platform CSPRNG.
- A nonce must never repeat with the same derived AEAD key.
- Clients must reserve the nonce in the encrypted local database in the same transaction as the pending outbox/envelope write.
- Retries reuse the already-created envelope bytes; they do not re-encrypt with the same nonce and different plaintext.
- If local state finds an attempted duplicate `(content_key_id, key_epoch, nonce)`, encryption fails with `encrypt_reject_duplicate_nonce` before producing ciphertext.

XChaCha20's nonce size makes accidental collision unlikely, but the ledger requirement is still mandatory because offline retries, backup restore and multi-process writes can otherwise create deterministic reuse bugs.

## 7. Canonical associated data

Build the AAD document with exactly these fields and canonicalize it with RFC 8785 JCS:

```json
{
  "protocol_version": 1,
  "suite_id": "TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256",
  "space_id": "sp_alpha",
  "entity_id": "ent_example",
  "entity_version": 1,
  "key_epoch": 1,
  "content_key_id": "ck_space_alpha_epoch_1"
}
```

The AEAD AAD bytes are the canonical UTF-8 JSON bytes, not the SHA-256 string. `aad.canonical_json_sha256` is stored for deterministic validation, indexing and cross-platform debugging.

Any change to space ID, entity ID, entity version, key epoch, content key ID, protocol version or suite ID must produce AEAD authentication failure.

## 8. Encrypted entity envelope

Normative logical shape:

```json
{
  "protocol_version": 1,
  "suite_id": "TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256",
  "space_id": "sp_alpha",
  "entity_id": "ent_success",
  "entity_version": 1,
  "key_epoch": 1,
  "content_key_id": "ck_space_alpha_epoch_1",
  "nonce": "00112233445566778899aabbccddeeff0011223344556677",
  "aad": {"canonical_json_sha256": "...64 lowercase hex..."},
  "ciphertext": "...lowercase hex ciphertext plus tag...",
  "ciphertext_sha256": "...64 lowercase hex..."
}
```

Validation order:

1. Parse JSON with duplicate-key rejection.
2. Reject unknown protocol versions, deprecated protocol versions or unknown suite IDs before decryption.
3. Validate field types, lowercase encoding, length bounds and allowed enum values.
4. Check server authorisation for the opaque space/entity route without inspecting protected content.
5. Recompute canonical AAD bytes and compare `aad.canonical_json_sha256`.
6. Load the matching content key by `content_key_id` and `key_epoch`; fail with typed key-unavailable errors if absent.
7. Decrypt with XChaCha20-Poly1305 and canonical AAD bytes.
8. Reject rollback/replay by comparing server sequence and entity version markers before plaintext is made visible to application logic.

## 9. Plaintext payload boundary

Plaintext payloads are client-only and encrypted inside `ciphertext`. They may contain protected fields from ADR-0003, including task titles, descriptions, list/project/label names, comments, reminder details, recurrence expressions, completion state, priority and attachment names/previews.

Clients may store those fields in SQLCipher-protected local tables for search, reminders, recurrence and UI. They must not send them to the server or put them in logs, notifications, telemetry, support exports or unencrypted fixtures.

## 10. Attachment chunking, integrity and resume

Attachment entity envelopes point to an encrypted attachment manifest. The manifest is protected content and includes plaintext filename, media type, preview metadata and user-visible attachment description.

Server-visible attachment chunk metadata is limited to opaque attachment ID, chunk index, chunk count, chunk size, ciphertext length/hash, storage location, upload session ID and lifecycle state.

Chunk rules:

- Default plaintext chunk size: 1 MiB unless a platform-specific memory limit requires a smaller reviewed value.
- Each chunk encrypts with the attachment data key and a fresh 24-byte nonce.
- Each chunk AAD includes protocol version, suite ID, space ID, attachment ID, key epoch, chunk index, chunk count and plaintext chunk size.
- The encrypted manifest stores SHA-256 hashes of plaintext chunks and the whole plaintext stream for client-side integrity after download/resume.
- The server may verify ciphertext SHA-256 for storage integrity but cannot verify plaintext hashes.
- Resume is keyed by upload session ID plus chunk index; re-uploading a chunk must send the identical ciphertext or a new upload session.

## 11. Replay, rollback and substitution controls

Clients maintain durable encrypted local markers:

- highest accepted server sequence per space;
- highest accepted entity version per `(space_id, entity_id)`;
- accepted tombstone marker per entity;
- nonce reservation ledger per `(content_key_id, key_epoch)`.

A valid older envelope below the local sequence/entity marker is a rollback attempt and must fail closed with `sync_reject_replay_rollback`. Rebinding ciphertext to another space, entity ID or version must fail AEAD authentication. Replaying an identical already-accepted envelope is idempotent only if server sequence and entity version match the local accepted record exactly.

## 12. Rotation and migration

Rotation creates a new random space content key, key epoch and content-key ID. Authorised clients receive the new wrapped key through a future provisioning/sharing protocol. New writes immediately use the new epoch.

Migration options:

- Lazy migration: old envelopes remain readable with retained old keys and are rewritten when edited.
- Active migration: clients re-encrypt selected entities and attachment keys under the new epoch when online and authorised.
- Revocation migration: after member/device revocation, clients should stop delivering old keys and re-encrypt data whose future confidentiality matters; past decrypted copies cannot be clawed back.

Deprecated protocol versions remain parseable only enough to identify their version and return `reject_deprecated_version`; they must not decrypt after the deprecation policy says to fail closed.

## 13. Fail-closed typed errors

Implementations should expose typed categories without plaintext content:

- `reject_unsupported_version`
- `reject_deprecated_version`
- `decrypt_reject_wrong_key`
- `decrypt_reject_wrong_aad`
- `decrypt_reject_tamper`
- `decrypt_reject_wrong_space`
- `decrypt_reject_wrong_entity_id`
- `decrypt_reject_wrong_version`
- `sync_reject_replay_rollback`
- `encrypt_reject_duplicate_nonce`
- `key_unavailable`
- `canonicalization_error`
- `malformed_envelope`

Logs and server responses may include only these categories, opaque request/object IDs and safe operational metadata.

## 14. Test vectors

Committed vectors live under `testdata/e2ee/v1/`:

- `schema.json` documents the stable JSON shape.
- `vectors.json` contains deterministic synthetic vectors for success, wrong key/AAD, tamper, wrong space/entity ID/version, replay/rollback marker, duplicate nonce prevention, rotated key, unsupported version and deprecated version.
- `scripts/validate-e2ee-vectors.py` validates the schema subset and required scenario coverage using only Python's standard library.

The vectors are synthetic and safe to commit. They do not contain real user data, real secrets or production key material. Future implementation tasks must add true cryptographic round-trip vectors generated from audited libraries while preserving the v1 schema compatibility contract.
