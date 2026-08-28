# ADR-0005 — Phase 1 encrypted offline sync and conflict resolution

Status: Accepted
Date: 2026-08-28
Accepted: 2026-08-28
Decision owner: Paul
Related task: Notion page `3b0319db-76c8-81c4-9fdd-db56b3de5650`
Acceptance evidence: the source Notion page records `Approved decisions — 2026-08-28` and says Paul approved all six `Decisions requested from Paul`.
Depends on: [ADR-0001](0001-phase-1-e2ee-mandatory.md), [ADR-0003](0003-phase-1-privacy-threat-model-and-metadata-boundary.md), [ADR-0004](0004-phase-1-e2ee-protocol-and-encrypted-entity-envelope.md)

## Context

ADR-0001 requires E2EE before any usable release stores or synchronises real task data. ADR-0003 defines the Phase 1 privacy threat model and metadata boundary: task semantics, object type, priority, due-date presence, completion state, coarse reminder existence, reminder details, recurrence expressions, comments, attachment names/previews, search terms, and content keys must not be visible to the server. ADR-0004 defines the accepted encrypted entity envelope, associated-data binding, key epochs, replay/rollback rejection, typed errors, and protocol migration boundary.

Phase 1 still needs an accepted sync and conflict-resolution architecture that preserves those decisions while allowing trusted Android/WearOS clients to work offline and later converge across devices. The server may authenticate devices, authorise membership, store opaque ciphertext, assign ordering, deduplicate retries, and serve ordered changes, but it must not decrypt, inspect, index, log, or resolve semantic task content.

This ADR records the already approved architecture from Notion task `3b0319db-76c8-81c4-9fdd-db56b3de5650`. It is documentation only. It does not implement sync, storage, APIs, clients, cryptography, provisioning, recovery, sharing, migrations, deployment, or feature behaviour.

## Decision

Adopt an append-only encrypted operation log per private space as the Phase 1 sync model.

Trusted clients create, encrypt, authenticate, persist, upload, download, decrypt, merge, and apply operations. The server authenticates devices and memberships, stores opaque encrypted operation envelopes, assigns a strictly increasing per-space server sequence, enforces transactional idempotency for `(space_id, op_id)`, deduplicates retries, and serves ordered changes after a client's durable pull cursor. The server never decrypts operations or receives plaintext task semantics.

Each operation uses a stable random 128-bit operation ID. Retrying an upload resends the same ciphertext and the same `op_id`; it does not re-encrypt the same logical write into a different envelope. Server persistence for `(space_id, op_id)` is transactional: either the existing accepted ciphertext/sequence is returned for an identical retry, or a conflicting reuse of the same operation ID is rejected as a privacy-safe typed error.

Clients advance a durable pull cursor atomically with applying each downloaded page. A crash must leave the client in one of two safe states: either the page was not applied and the cursor was not advanced, or the page was fully applied and the cursor advanced to the last applied sequence. Cursor recovery re-pulls from the last durable sequence and re-applies already accepted operations idempotently.

Conflict resolution is client-side and deterministic after decryption. Entities use causal field-level registers:

- independent field edits merge;
- concurrent edits to the same field use deterministic server-sequence tie-break;
- losing same-field values for material text remain reviewable to the user or future UX instead of being silently discarded;
- server-assigned sequence is used only as a deterministic ordering input, not as semantic authority over decrypted content.

Delete wins for an entity generation. A restore explicitly creates the next generation. Compacted state retains the highest deleted generation so old operations from earlier generations cannot resurrect deleted content. A later generation may exist only through an explicit restore operation that creates that generation.

Clients may author encrypted snapshots to reduce replay cost. Recovery applies a trusted client-authored snapshot plus operation-tail replay. The approved starting compaction policy is a proposed 90-day grace window before old operation tails become eligible for compaction. The exact production retention/deletion workflow remains a later gate, but implementations must preserve compatible fallback/full-replay behaviour: if a snapshot is unavailable, rejected, too new, or not trusted for the client's key epoch/protocol support, the client falls back to an earlier valid snapshot or full operation replay.

## Privacy and encryption boundary

The Phase 1 server-visible boundary remains the one accepted by ADR-0003 and ADR-0004.

Server-visible sync metadata is limited to operationally necessary opaque values such as account/device/member IDs, opaque `space_id`, opaque `op_id`, protocol/suite/version IDs, key epoch/content-key ID, ciphertext length/hash, request IDs, server sequence, timestamps needed for ordering/rate limits/storage, and privacy-safe typed error categories.

The following remain encrypted and must not appear in plaintext server payloads, schema fields, logs, metrics, diagnostics, CI artifacts, support exports, object-storage metadata, or push payloads:

- task/list/project/label/comment/reminder/recurrence/search content;
- object type and semantic operation type;
- priority, due-date presence, completion state, and coarse reminder existence;
- reminder details, recurrence expressions, search terms, attachment filenames/previews, and plaintext attachment hashes;
- content keys, database keys, wrapped-key plaintext, recovery secrets, and decrypted sync keys.

Encrypted operation envelopes must preserve ADR-0004 AEAD associated-data binding for protocol version, suite ID, space ID, entity ID or operation target identifier where applicable, version/generation, key epoch, content-key ID, and canonical payload hash. Clients must reject replay, rollback, wrong-space, wrong-entity, wrong-version, wrong-key, tamper, substitution, unsupported-version, and deprecated-version cases before plaintext is exposed.

Phase 1 diagnostics remain local-only. Logs and errors may include opaque IDs, request IDs, server sequences, redacted counts, capability categories, and typed error categories only. They must not include decrypted content, semantic field names, user-authored names, filenames, search strings, reminder details, recurrence expressions, or key material.

## Compatibility and invariants

Future implementations must preserve these invariants before claiming compatibility with ADR-0005:

1. Idempotency: `(space_id, op_id)` is unique and transactional. Identical retries return the original accepted result. Reusing an `op_id` for different ciphertext, AAD, key epoch, or operation bytes is rejected.
2. Stable ciphertext retries: clients persist the encrypted operation before upload and retry by resending those bytes. They do not generate a replacement ciphertext for the same logical operation.
3. Monotonic server sequence: the server assigns strictly increasing per-space sequences and returns ordered changes. It may not skip, reorder, or rewrite accepted sequences for a client view.
4. Atomic cursor recovery: clients apply a pulled page and advance the durable cursor in one transaction or equivalent crash-consistent unit. Recovery can safely re-pull and idempotently re-apply from the last durable cursor.
5. Client-side convergence: clients with the same authorised key material and complete ordered operation history converge to the same decrypted state without server-readable semantics.
6. Field-level merge: independent fields merge; same-field concurrency uses the deterministic server-sequence tie-break; reviewable losing material-text values are retained for future user-facing resolution.
7. Delete/restore generations: delete wins within a generation; restore creates the next generation; the highest deleted generation survives compaction and blocks stale resurrection.
8. Replay and substitution rejection: ADR-0004 AAD/version/key bindings prevent rebinding ciphertext across spaces, entities, versions, operations, generations, key epochs, or protocol suites.
9. Revocation boundary: device/member revocation stops future sync authorisation and key delivery. It cannot erase content already decrypted, copied, exported, screenshotted, cached, or retained by a previously authorised client or human.
10. Key rotation: new writes after rotation use the new key epoch/content-key ID. Old epochs remain readable only for authorised clients that still need replay or migration. Revocation-sensitive data may require client re-encryption under a later sharing/revocation design.
11. Snapshots and compaction: snapshots are client-authored and encrypted. Operation-tail compaction must not break clients that need full replay, older snapshots, old key epochs, or protocol migration handling.
12. Clock skew: semantic conflict resolution does not depend on wall-clock trust. Client timestamps may remain encrypted content; server receive timestamps may be operational metadata but cannot decide decrypted task semantics.
13. Privacy-safe diagnostics: sync health, conflict, idempotency, cursor, key, and envelope failures use redacted typed errors and opaque IDs only.
14. Protocol migration: unsupported or deprecated protocol/suite versions fail closed according to ADR-0004. New sync semantics that alter server-visible metadata, key hierarchy, sharing boundaries, or conflict rules require a new accepted ADR.

## Verification expectations for future implementation

This ADR does not add executable tests and does not claim any sync implementation exists. Future server, Android, WearOS, and contract-test work should add synthetic fixtures, simulations, and test vectors that verify at least:

- idempotent upload retry with the same ciphertext and `(space_id, op_id)`;
- rejection of conflicting operation-ID reuse;
- cursor crash recovery before apply, after partial apply, and after full page apply;
- ordered replay convergence across multiple offline clients;
- field-level merge of independent fields;
- same-field deterministic tie-break with retained reviewable losing material-text values;
- delete-wins and explicit restore into the next generation;
- stale operation replay after delete and after compaction grace;
- AEAD/AAD rejection for wrong space, entity, operation target, version, generation, key epoch, content-key ID, suite, or canonical payload hash;
- rollback/replay rejection below highest accepted server sequence and entity/generation markers;
- revocation blocking future sync/key delivery while documenting the inability to claw back already decrypted content;
- key rotation replay across old and new epochs for authorised clients;
- encrypted snapshot restore plus operation-tail replay, including fallback to earlier snapshot or full replay;
- protocol migration fail-closed behaviour for unsupported and deprecated versions;
- clock-skew cases where client wall-clock timestamps differ but server sequence still yields deterministic ordering;
- privacy-safe logs, errors, diagnostics, support artifacts, CI artifacts, and push payloads containing no protected content or key material.

These tests must use synthetic data only. Real-user data, production secrets, decrypted fixture corpora, or plaintext task content copied from users are not acceptable test inputs.

## Consequences

### Positive

- Offline-capable clients can create encrypted changes independently and later converge without trusting the server with task semantics.
- Server responsibilities stay operationally simple: authenticate, authorise, store ciphertext, assign per-space sequence, deduplicate, and return ordered opaque changes.
- Stable operation IDs and ciphertext retries make upload retry behaviour idempotent and auditable without plaintext.
- Atomic cursor advancement gives a clear crash-recovery contract for clients and avoids lost or skipped operations.
- Field-level registers reduce unnecessary conflicts while retaining deterministic convergence for same-field concurrency.
- Delete/restore generations prevent stale operation replay from resurrecting deleted entities after compaction.
- Client-authored encrypted snapshots provide a path to bounded replay cost without weakening the E2EE boundary.

### Negative / costs

- Clients carry the semantic complexity: operation construction, encrypted local outbox persistence, decryption, merge, conflict review state, cursor atomicity, snapshot validation, and recovery.
- The server cannot validate semantic operation types, field names, task state, reminder state, due dates, priority, or conflict intent.
- Deterministic server-sequence tie-break is simple and convergent but may not match user expectations for every concurrent edit; material text needs review UX later.
- Append-only operation history and 90-day compaction grace increase storage requirements until production retention/compaction policy is implemented.
- Snapshot trust, key-epoch availability, and protocol migration add compatibility obligations to future clients.

## Accepted limitations and unresolved dependencies

- Revocation prevents future access but cannot claw back plaintext already decrypted or copied by an authorised client or human.
- Server-visible account, membership, device, request, size, timing, sequence, storage, and billing metadata remains observable as allowed by ADR-0003.
- The 90-day compaction grace is the approved starting policy for architecture; production retention, deletion, purge, billing, and legal workflows remain future decisions.
- Losing all authorised devices and recovery material may make encrypted data permanently unrecoverable; account reset remains separate from content-key recovery.
- Usable user-facing conflict review, restore UX, and recovery UX remain separate product gates.
- Supply-chain hardening and trusted client release provenance remain necessary before dogfood or production use with real synced data.

## Non-goals and future gates

This ADR does not implement or approve production behaviour. The following remain separate future gates:

- device identity and provisioning;
- initial PostgreSQL schema and migrations;
- server sync API, storage, sequencing, idempotency, and cursor implementation;
- Android, WearOS, or other client sync implementation;
- production cryptography and audited library integration;
- protocol migrations and production compatibility policy;
- content-key recovery, key backup, or account-recovery UX;
- sharing, member/device revocation implementation, key rotation workflows, and selective-sharing migration;
- feature UX for conflict review, restore, snapshots, diagnostics, reminders, sharing, or recovery;
- use of real user data, dogfood, alpha, beta, production deployment, or a usable release.

Technical approval, merge, and this ADR's Accepted status do not create deployment approval, release approval, human/business acceptance for later feature work, or permission to store/synchronise real user task data before the remaining gates are satisfied.
