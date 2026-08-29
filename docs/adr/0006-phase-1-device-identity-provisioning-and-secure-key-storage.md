# ADR-0006 — Phase 1 device identity, provisioning, and secure key storage

Status: Proposed
Date: 2026-08-29
Decision owner: Paul
Related task: Notion page `3b0319db-76c8-8143-bbb8-f8f434098c9f`
Specification: [`docs/device-identity-provisioning.md`](../device-identity-provisioning.md)
Depends on: [ADR-0001](0001-phase-1-e2ee-mandatory.md), [ADR-0002](0002-encrypted-client-sqlite-persistence.md), [ADR-0003](0003-phase-1-privacy-threat-model-and-metadata-boundary.md), [ADR-0004](0004-phase-1-e2ee-protocol-and-encrypted-entity-envelope.md), [ADR-0005](0005-phase-1-encrypted-offline-sync-and-conflict-resolution.md)

## Context

ADR-0001 makes end-to-end encryption mandatory before any usable release stores or synchronises real task data. ADR-0002 chooses SQLCipher-backed Android/WearOS persistence with Android Keystore-protected local database keys and no Android backup of protected stores until recovery is designed. ADR-0003 limits server-visible metadata and forbids plaintext protected content, key material, user-facing device names, detailed device fingerprints, and sensitive diagnostics outside trusted clients. ADR-0004 defines the accepted encrypted entity envelope and primitive suite. ADR-0005 defines encrypted offline sync, idempotent encrypted operation replay, client-side semantic conflict handling, and the revocation boundary.

Phase 1 now needs a bounded device identity and provisioning decision so future Android, WearOS, server, sync, recovery, sharing, and key-rotation work do not invent incompatible key ownership, pairing, local storage, or server metadata. The Notion source task asks for trusted devices, Android Keystore wrapping, hardware/software capability handling, authenticated approval, QR/short-code pairing without exposing secrets to the server, inventory/revocation/stale-device policy, WearOS provisioning from the paired Android device, future platform compatibility, and reinstall/restore/biometric-change behaviour.

This ADR is Proposed only. Paul's `APPROVE DEVICE-ID SPEC` authorization on 2026-08-29 allowed this specification/review cycle; it is not Human Acceptance of this ADR and does not authorise implementation, merge, deployment, Notion completion, or production cryptography.

## Decision

Adopt [`docs/device-identity-provisioning.md`](../device-identity-provisioning.md) as the Proposed Phase 1 device identity, provisioning, and secure key-storage specification for review.

If later accepted, Phase 1 device provisioning will use these boundaries:

1. The first Android phone is the primary bootstrap device. It generates local database key material, device identity/signing material, device key-agreement material, and initial space content keys on device using platform CSPRNG and audited cryptographic libraries.
2. Android Keystore protects local database keys, wrapping keys, and device-private material where supported. Hardware-backed protection is preferred but not universal; software-backed fallback must be detected and surfaced honestly as a capability/risk state, not represented as equivalent to hardware-backed storage.
3. Android backup remains disabled or explicitly excludes SQLCipher databases, WAL/journal/schema/FTS sidecars, key-wrapper storage, wrapped device keys, nonce ledgers, pairing state, attachment caches, token/key material, logs, screenshots, and other protected stores until a separate recovery/key-backup design is accepted.
4. Account authentication and password reset remain separate from content-key recovery. The server must never receive private device keys, plaintext local database keys, plaintext space/content keys, wrapping keys, pairing secrets, or recovery secrets.
5. Device inventory uses opaque identifiers, public keys, key versions, lifecycle states, key epochs/content-key IDs needed for routing, trust/revocation timestamps, idempotency keys, expiry, and privacy-safe typed errors only. User-facing device names, detailed platform/capability/fingerprint values, protected object semantics, and sensitive diagnostics remain encrypted or local unless Paul later accepts a narrow privacy exception.
6. New-device approval requires an existing trusted device or accepted future recovery mechanism. Candidate devices authenticate to the account but receive no content keys until explicit user approval, transcript-bound mutual authentication, expiry, replay protection, and signed key-receipt acknowledgement complete.
7. Pairing/provisioning uses primitives and versioning compatible with ADR-0004: XChaCha20-Poly1305 for AEAD, HKDF-SHA-256 for derivation, SHA-256 transcript hashes, platform CSPRNG, explicit suite/protocol version IDs, and fail-closed handling for unknown or deprecated versions. Ed25519 device identity signatures and X25519 key agreement are the Proposed default for device-to-device authentication and provisioning, subject to audited library availability and implementation review.
8. WearOS Phase 1 provisioning is companion-mediated by a currently trusted Android phone. Standalone watch provisioning, independent watch recovery, Apple clients, browser clients, and non-Android-mediated device approval are future decisions.
9. Device lifecycle states are explicit: local unregistered, pending approval, provisioning in progress, trusted active, stale suspected, key rotation required, revoked, expired, provisioning failed, and reset local-only.
10. Revocation blocks future sync authorisation and future wrapped-key delivery. New key epochs omit revoked devices. Revocation cannot erase plaintext, screenshots, exports, old keys, or cached content already retained by a compromised or previously authorised device.
11. Recovery is intentionally separate from provisioning. Phase 1 does not approve server escrow, plaintext key backup, password-derived content-key recovery, or operator recovery. Losing all authorised devices and recovery material may make encrypted content permanently unrecoverable.
12. Implementations must expose privacy-safe typed failures and deterministic/synthetic tests for Keystore capability/invalidation, backup exclusion, pairing transcript binding, MITM/replay rejection, retry/idempotency, WearOS transfer, server metadata boundaries, revocation, and protocol version compatibility.

## Consequences

### Positive

- Future Android, WearOS, server, sync, sharing, recovery, and key-rotation tasks get one reviewable boundary for device ownership, local key storage, and provisioning.
- The server can authenticate devices, route encrypted pairing messages, keep inventory, enforce expiry/idempotency/rate limits, and revoke future access without holding content-decryption keys or pairing secrets.
- The design preserves ADR-0003's metadata boundary and ADR-0004's primitive/versioning strategy instead of inventing a conflicting crypto suite.
- Companion-mediated WearOS provisioning gives the project a bounded Android-first watch path while deferring standalone and Apple-platform complexity.
- Revocation and recovery limitations are explicit, so UI and support copy cannot overpromise remote wipe or guaranteed data recovery.

### Negative / costs

- Clients carry significant security complexity: Keystore capability handling, credential-change invalidation, local no-backup policy, nonce/session ledgers, transcript binding, user verification, key receipts, and lifecycle state transitions.
- Users without hardware-backed secure storage may have weaker local protection unless a later release-hardening decision blocks those devices.
- No Phase 1 server-side content recovery means users can permanently lose encrypted data if all authorised devices and future recovery material are lost.
- Keeping user-facing device names and detailed platform data off the server makes inventory/support UX harder; local encrypted inventory and opaque server records must be designed carefully.
- A malicious authorised client or compromised endpoint can still exfiltrate plaintext after decryption; E2EE does not protect against a malicious trusted endpoint.

## Required implementation rules after acceptance

If this ADR is later accepted, future implementation tasks must:

1. Store private device keys, local database keys, plaintext space/content keys, wrapping keys, pairing secrets, and recovery secrets only on trusted clients; never send them to the server, logs, telemetry, CI artifacts, support bundles, or backups.
2. Use Android Keystore and no-backup storage according to the specification. Do not claim hardware backing unless the platform reports it for the relevant key operation.
3. Treat key-unavailable, key-invalidated, corrupted-wrapper, pairing mismatch, replay, unsupported-version, and recovery-unconfigured cases as typed states that fail closed without overwriting encrypted data.
4. Require explicit user approval and transcript verification before provisioning any new device or companion watch with content keys.
5. Keep server-visible inventory metadata minimal, opaque, and justified by ADR-0003; do not add plaintext device display labels, detailed platform fingerprints, protected object semantics, or sensitive diagnostics without a new accepted decision.
6. Make provisioning retry/idempotency stable: identical retries return the original accepted result; conflicting reuse of device IDs, session IDs, nonces, transcripts, or key receipts is rejected.
7. Deny sync/key delivery to revoked devices and make future key epochs omit them, while documenting that past plaintext/old keys cannot be clawed back.
8. Keep recovery/account reset separate from provisioning unless a future recovery/key-backup ADR explicitly changes that boundary.
9. Use deterministic synthetic tests and vectors only; no real user content, production keys, plaintext protected fixtures, or secrets.
10. Fail closed on unknown, deprecated, or unsupported provisioning protocol/suite versions before accepting wrapped keys.

## Decisions requested from Paul before acceptance

| Decision | Recommended default | Trade-offs |
| --- | --- | --- |
| Permit software-backed Keystore devices for Phase 1 personal/self-host use? | Permit with local warning and no hardware-backed claim. | Broader compatibility and development support; weaker protection on compromised/rooted devices. |
| Fresh user authentication policy for key use? | Require fresh user presence for provisioning, recovery, and key-policy changes, not every background sync. | Better usability/offline reminders; currently unlocked compromised devices remain a risk. |
| Server-visible client class/platform? | Do not store by default; use opaque routing/capability tokens and encrypted/local labels. | Stronger privacy; harder routing/support/debugging. |
| Stale-device handling? | Mark stale after 30 days without check-in/key receipt, but require explicit user revocation. | Avoids accidental lockout; stale devices remain eligible until user acts. |
| Rooted/debug/emulator posture? | Warn locally and defer production hard-blocking to release-hardening. | Keeps development/self-hosting practical; production assurance may need later tightening. |
| Recovery posture for Phase 1? | No content recovery unless an authorised device remains; account reset cannot recover content keys. | Honest E2EE boundary; users may permanently lose data. |
| WearOS scope? | Companion-mediated watch provisioning only. | Bounded Android-first delivery; standalone/Apple clients need later design. |

## Alternatives considered

### Server escrow of content keys

Rejected because it violates ADR-0001 and ADR-0003. The service or operator would be able to decrypt protected task content or recover it through account reset.

### Password-derived or cloud-backed content recovery in this ADR

Deferred. Recovery changes the threat model and needs separate decisions for KDF parameters, rate limits, lockout, recovery UX, backup storage, rotation, account reset interaction, and support expectations.

### Require hardware-backed keys for every device

Not selected as the Proposed default because hardware backing is not universal and would block some legitimate Android/WearOS/self-host/development use. Hardware-backed protection remains preferred and must be reported honestly.

### Plaintext server-visible device names and platform details

Rejected as the default. Human-readable device labels and detailed capability fingerprints can identify users and are not necessary for encrypted key routing. Any future exception must be explicit and narrow.

### Standalone WearOS provisioning in Phase 1

Deferred. Companion-mediated Android phone provisioning is sufficient for the minimum watch path and avoids premature standalone watch recovery, input, and trust UX decisions.

## Acceptance and review state

ADR-0006 is Proposed. It must remain Proposed until Paul separately records Human Acceptance after independent technical review. This ADR does not modify or supersede Accepted ADR-0001 through ADR-0005.
