# ADR-0004 — Phase 1 E2EE protocol and encrypted entity envelope

Status: Accepted
Date: 2026-08-28
Accepted: 2026-08-28
Decision owner: Paul
Related task: Notion page `3b0319db76c88193b4c0ee38b28a4fd3`
Specification: [`docs/e2ee-protocol-v1.md`](../e2ee-protocol-v1.md)
Test vectors: [`testdata/e2ee/v1/`](../../testdata/e2ee/v1/)

## Context

ADR-0001 requires end-to-end encryption before any usable release stores or synchronises real task data. ADR-0002 requires Android and WearOS local persistence to keep protected content in encrypted SQLite and out of plaintext side channels. ADR-0003 defines the accepted Phase 1 privacy threat model and metadata boundary.

The next implementation tasks need one protocol, envelope and vector source instead of per-client invention. The accepted protocol must let an untrusted server store, sequence and return encrypted objects while Android, WearOS, Go contract tests and future clients agree on canonical bytes, key identifiers, algorithm identifiers and fail-closed behaviour.

Paul raised per-space content keys as the preferred baseline. This ADR evaluates that baseline against blast radius, rotation/revocation, nonce uniqueness, attachments and selective sharing before deciding whether every entity also needs a wrapped per-entity data key.

## Decision

Adopt `docs/e2ee-protocol-v1.md` as the accepted Phase 1 E2EE protocol and encrypted-entity-envelope specification.

Use only audited, standard primitives and maintained platform libraries:

- AEAD: XChaCha20-Poly1305 with 256-bit keys and 192-bit nonces.
- KDF/wrapping derivation: HKDF-SHA-256.
- Hashing for canonical AAD/vector integrity and attachment chunk manifests: SHA-256.
- Randomness: platform CSPRNG only (`SecureRandom` on Android/WearOS; `crypto/rand` on Go).
- Canonical serialization: RFC 8785 JSON Canonicalization Scheme (JCS), UTF-8, no platform-specific map ordering.

Library strategy:

- Android and WearOS use Google Tink's Java/Kotlin XChaCha20-Poly1305 AEAD and HKDF/stream-safe primitives where available, with Android Keystore used only for local device wrapping as described by ADR-0002. If a minimum platform lacks the required Tink primitive, implementation must block rather than substitute a custom cipher.
- Go server and contract-test code use `golang.org/x/crypto/chacha20poly1305` for XChaCha20-Poly1305, `crypto/hkdf` or the Go 1.23 equivalent for HKDF-SHA-256, `crypto/sha256`, and a reviewed JCS implementation or in-repo conformance tests before accepting envelopes.
- Future clients must use audited libraries exposing the same XChaCha20-Poly1305, HKDF-SHA-256, SHA-256 and JCS behaviours. They must validate the committed vectors before joining sync.

Use one active content key per space epoch as the baseline for normal encrypted objects. Do not add envelope-wrapped per-object data keys for every small object in Phase 1. This keeps writes small, simplifies offline sync, reduces wrapping metadata and matches the expected personal/small-team workload. Each envelope records `content_key_id` and `key_epoch`; clients derive operation-specific AEAD subkeys from the space content key using HKDF context, so nonce uniqueness is enforced per derived content-key epoch.

Require separate encrypted attachment keys for attachment blobs. Each attachment has an attachment data key wrapped by the current space content key, because attachments have larger blast radius, chunked upload/resume needs independent manifests, and future selective sharing or retention can rotate attachment material without rewriting every task entity.

## Server-visible boundary

The server may store and sequence only ADR-0003-allowed operational metadata plus opaque encrypted envelope bytes and hashes:

- account/user/device IDs needed for authentication and routing;
- space membership IDs and roles needed for authorisation;
- opaque `space_id`, opaque `entity_id`, `key_epoch`, `content_key_id`, protocol/suite/version IDs, ciphertext length/hash, server sequence, timestamps, tombstone/storage lifecycle markers and object-storage locations;
- encrypted object bytes and encrypted attachment chunks.

The server must not receive plaintext protected fields named in ADR-0003: task/list/project/label/comment/reminder/recurrence/search content; object type semantics; priority; due-date presence; completion state; coarse reminder existence; attachment names/previews; or content keys. API, logs, metrics, errors, support exports and CI fixtures must use only opaque IDs and typed error categories.

## Replay, rollback and substitution controls

The envelope authenticates protocol version, suite ID, space ID, entity ID, entity version, key epoch, content-key ID and canonical payload hash as AEAD associated data. Clients must reject ciphertext if any of those values are rebound to another space/entity/version or key epoch.

Clients maintain the highest accepted server sequence and entity version per space/entity. A syntactically valid envelope below those markers is treated as rollback/replay and rejected before plaintext is exposed. Servers sequence envelopes monotonically but are not trusted to decide semantic freshness; clients verify markers after sync and fail closed on gaps, rewinds or duplicate nonces.

## Rotation, revocation and sharing

Rotating a space content key creates a new epoch and content-key ID. New writes use the new epoch immediately. Existing envelopes can remain readable with retained old keys until clients rewrite them; security-sensitive revocation may require re-encrypting affected entity content and attachment keys. Revocation stops future sync authorisation and key delivery but cannot erase content already decrypted by an authorised client, matching ADR-0003.

Per-space keys are acceptable for Phase 1 because selective sharing is deferred and spaces are the natural sharing boundary. If future selective sharing requires entity-level access, a new ADR must introduce per-entity or per-subtree key wrapping and migration rules rather than silently changing v1 envelopes.

## Consequences

### Positive

- Android, WearOS, Go server contract tests and future clients get one versioned protocol, envelope schema and fixture set.
- The server can store, order and return encrypted envelopes without plaintext content keys or protected fields.
- XChaCha20-Poly1305's large nonce space reduces accidental nonce collision risk for offline clients while still requiring explicit nonce reservation checks.
- Per-space content keys minimise ordinary entity write amplification; attachment-specific keys contain large-object and resume blast radius.
- Fail-closed version and suite IDs give a clean path for protocol deprecation and migration.

### Negative / costs

- Clients carry protocol complexity: JCS serialization, nonce ledgers, rollback markers, key epochs and typed error handling.
- The server cannot inspect object semantics for validation, scheduling, search or conflict resolution.
- Revocation and selective sharing remain coarse-grained until later ADRs define provisioning, recovery, key backup and sharing flows.
- Protocol vector fixtures are synthetic in this slice; real client/server implementations must add cryptographic round-trip vectors when libraries are integrated.

## Non-goals

- No production client/server crypto implementation, deployment, real user data, live secrets, recovery flow, device provisioning, key backup, sharing UI, telemetry or support export is implemented by this ADR.
- Acceptance of this protocol does not authorise production cryptography, deployment, device provisioning, recovery/key backup, sharing/revocation implementation, real cryptographic round-trip vectors or feature work. Those remain separate future gates.
- This ADR does not change ADR-0001, ADR-0002 or ADR-0003.

## Required implementation rules after acceptance

1. Implementations must reject unsupported, deprecated or unknown `protocol_version`/`suite_id` before decrypting.
2. Implementations must use audited library primitives exactly matching the suite; no custom ciphers, MACs, KDFs, nonce derivations or canonicalizers.
3. Clients must reserve a nonce before encrypting and persist the reservation with the pending outbox write so retries never reuse a nonce with the same derived content key.
4. Clients must authenticate all envelope identity/version/key fields as associated data and reject wrong key, AAD, tamper, wrong space/entity/version and rollback cases without releasing plaintext.
5. Servers must validate only envelope shape, authorisation, size limits, quotas and sequence rules that do not require decrypted content.
6. Android, WearOS, Go server contract tests and future clients must pass `testdata/e2ee/v1/` vectors before they can claim v1 compatibility.
7. Future recovery, device provisioning, sharing, revocation and selective-sharing tasks must preserve ADR-0003's server-visible metadata boundary unless a new accepted ADR supersedes it.

## Acceptance and review state

ADR-0004 is Accepted as of 2026-08-28.

Acceptance evidence:

- Paul explicitly accepted the corrected Phase 1 E2EE protocol by replying `ACCEPT E2EE PROTOCOL` on 2026-08-28; the Notion source task `3b0319db76c88193b4c0ee38b28a4fd3` was read back with Human Acceptance=true.
- Independent technical approval completed in `t_58192039` after the correction cycle `t_819078b2` -> `t_65c303a8` -> `t_59f43297` -> `t_94476219`.
- PR #8 head `1ba5acea65083f03e7eec9279dec3becb4d10c43` was the corrected proposal reviewed against base `3d8119e2a1c89268529705831a31c95b418358c8`.
- PR #8 was integrated into `main` as merge commit `e98bc1523395560888e26b7cc6915eae3981daaa` on 2026-08-28T13:29:17Z.

Production implementation, deployment, device provisioning, recovery/key backup, sharing/revocation implementation, real cryptographic round-trip vectors and Notion delivery state remain separate gates.
