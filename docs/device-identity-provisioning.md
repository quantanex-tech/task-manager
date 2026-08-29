# Phase 1 device identity, provisioning, and secure key storage

Related ADR: [ADR-0006](adr/0006-phase-1-device-identity-provisioning-and-secure-key-storage.md)
Related Notion task: `3b0319db-76c8-8143-bbb8-f8f434098c9f`
Status: Proposed Phase 1 specification for review; not accepted and not implemented.

## Purpose

This specification defines the Phase 1 device identity, provisioning, local key-storage, pairing, revocation, and recovery boundaries that later Android, WearOS, server, sync, sharing, and recovery work must preserve.

It is documentation only. It does not implement production cryptography, Android/WearOS code, server APIs, schemas, migrations, endpoints, release artifacts, deployment, Notion status changes, or real key material.

## Normative inputs

- [ADR-0001](adr/0001-phase-1-e2ee-mandatory.md): E2EE is mandatory before any usable release stores or syncs real task data.
- [ADR-0002](adr/0002-encrypted-client-sqlite-persistence.md): Android and WearOS use SQLCipher-backed local persistence with Android Keystore-protected database keys; Android backup remains disabled or explicitly excludes database and key-wrapper state until encrypted recovery is accepted.
- [ADR-0003](adr/0003-phase-1-privacy-threat-model-and-metadata-boundary.md) and [privacy threat model](security/privacy-threat-model.md): the server is not trusted with protected content, private keys, content keys, database keys, user-facing device names, semantic object fields, or sensitive diagnostics.
- [ADR-0004](adr/0004-phase-1-e2ee-protocol-and-encrypted-entity-envelope.md) and [E2EE protocol v1](e2ee-protocol-v1.md): Phase 1 encrypted payloads use the accepted v1 suite and fail-closed versioning.
- [ADR-0005](adr/0005-phase-1-encrypted-offline-sync-and-conflict-resolution.md): sync is append-only encrypted operation log replay with opaque metadata, stable idempotent retries, device/member revocation boundaries, and client-side semantic conflict handling.

## Goals

- Keep private device keys, plaintext space/content/database keys, pairing secrets, and recovery material off the server.
- Let a first Android phone bootstrap local encrypted storage and create the first trusted device identity without blocking local use on server-side content-key custody.
- Let a trusted Android phone provision a companion WearOS watch for a bounded Phase 1 subset without inventing a standalone watch recovery model.
- Let an existing trusted device approve a new device with mutual authentication, transcript binding, expiry, replay resistance, and privacy-safe server relay.
- Define inventory, lifecycle, revocation, stale-device, logging, API/event, test, threat, migration, and unresolved-decision boundaries before implementation.

## Non-goals

- No production crypto implementation or audited-library integration.
- No Android, WearOS, Go server, schema, migration, endpoint, UI, telemetry, backup, or release implementation.
- No approval of production dogfood, alpha, beta, deployment, or storage/sync of real user data.
- No content-key recovery, social recovery, password-derived recovery, cloud key escrow, or encrypted key backup implementation.
- No sharing/member-change UX beyond preserving key-epoch consequences and future compatibility.
- No standalone WearOS, iOS, macOS, web, browser-extension, or Apple Watch provisioning flow.

## Key and identifier boundaries

| Material | Purpose | Owner | Generation | Storage | Server visibility | Rotation / revocation | Backup / recovery status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Installation ID | App-install-local handle for local idempotency, logs, and bootstrap before a server-visible device record exists. It is not a cryptographic identity. | Single app install on one OS profile. | Random opaque 128-bit or larger value from platform CSPRNG on first launch. | App-private no-backup storage; may be mirrored inside SQLCipher once open. | Not sent by default. A derived opaque device ID is registered instead. | Changes on reinstall/reset. No revocation meaning. | Not backed up in Phase 1. Restore creates a new installation and requires provisioning/recovery. |
| Device ID | Opaque server-routable identifier for one trusted device registration. | Account/space membership plus one installed client. | Random opaque 128-bit or larger value, or UUID/ULID with no platform/user semantics, bound to the device identity public key. | SQLCipher and app-private no-backup metadata. | Visible as opaque `device_id` for auth, routing, inventory, last-seen, and revocation. | New device registration creates a new ID. Lost/stale devices transition to revoked/expired states; IDs are not reused. | Not recovered as the same device in Phase 1. Restored/reinstalled clients register as new devices. |
| Device identity signing key | Signs provisioning, inventory, sync/auth binding, and key-receipt statements so devices can authenticate one another without exposing private material. | One trusted device; public key belongs to account/device inventory. | Preferred default: Ed25519 key pair generated on device in Android Keystore when supported for signing; otherwise generated by an audited library and wrapped by a non-exportable Keystore wrapping key. | Private key non-exportable in Android Keystore when possible; software-backed/wrapped fallback only with local capability warning. Public key and key ID stored locally and on server. | Public key, key ID, algorithm/version, and revocation timestamp are visible. Private key is never visible. | Rotate by creating a successor key, signing a successor statement when possible, and re-wrapping future keys. Revocation blocks future auth/key delivery but cannot claw back old plaintext. | Private key is not backed up. Losing it requires new-device provisioning or future recovery. |
| Device key-agreement key | Establishes pairwise encrypted provisioning channels for new-device approval and phone-to-watch transfer. | One trusted device. | Preferred default: X25519 static or semi-static device key generated by audited library, wrapped locally by Keystore if Keystore cannot hold the primitive directly. Ephemeral X25519 keys are generated per pairing session. | Static private key in Keystore when available or wrapped in no-backup storage by a Keystore key; ephemeral private keys only in memory for one session. | Public agreement key and key ID may be visible as opaque inventory material. Ephemeral public keys may be relayed during pairing. Private and shared secrets are never visible. | Rotate with the identity key or after suspected compromise; revoked devices stop receiving wrapped future keys. | Not backed up in Phase 1. |
| Local database key | SQLCipher database key protecting local task data, indexes, outbox, nonce ledger, and device state at rest. | One app installation on one device. | Random 256-bit secret from platform CSPRNG on first database creation. | Wrapped by Android Keystore authenticated encryption or protected by a Keystore-held key; wrapper blob, IV, and metadata in app-private no-backup storage. | Never visible. | Can be rekeyed locally using SQLCipher `rekey` after generating and verifying a new key wrapper. Remote revocation does not change already-local DB access if the attacker controls/unlocks the device. | Android backup excluded. If wrapper/key is lost or invalidated, content is unavailable unless future recovery exists. |
| Space root / content keys | E2EE content keys for encrypted operations and object envelopes accepted by ADR-0004 and ADR-0005. | A private space and authorised devices/members. | Random 256-bit space content key per epoch, generated by a trusted authorised client. | Plaintext only in trusted client memory and SQLCipher while authorised; persisted wrapped for each authorised device using provisioning/sharing protocol. | Opaque `content_key_id`, `key_epoch`, protocol/suite/version are visible. Plaintext keys and wrapped-key plaintext are never visible. | Rotation creates new epoch/content-key ID; new writes use newest epoch. Revocation stops future key delivery and should trigger re-encryption where later sharing/revocation design requires. | No server recovery in Phase 1. Losing all authorised devices and recovery material may make content permanently unrecoverable. |
| Attachment data keys | Encrypt attachment blobs and manifests separately from normal small entity objects. | Attachment object in a space. | Random 256-bit key per attachment, generated by trusted client. | Wrapped by current space content key and stored inside encrypted client payloads/manifests. | Only opaque attachment IDs/chunk routing/ciphertext sizes/hashes are visible. | Rewrap or regenerate under future attachment rotation/migration design. | Same as space content keys; no Phase 1 plaintext backup. |
| Wrapping keys | Protect local database keys, device-private keys that cannot live directly in Keystore, and local cached wrapped content-key blobs. | One device/app installation. | Generated in Android Keystore with the strongest available non-exportable protection. | Android Keystore; wrapper metadata in no-backup storage. | Alias/version/capability category may be local-only; server does not need aliases. | Rotate when auth policy changes, compromise suspected, or migration requires it. Keep old wrapper until re-open/re-wrap verification succeeds. | Not backed up. Platform restore/reinstall invalidates or omits them by default. |
| Transport/session credentials | Authenticate the account/device to the server and protect network sessions. These are not content-decryption keys. | Account session and registered device. | Issued by auth service after user login and device registration; TLS sessions negotiated per connection. | OS credential storage/app-private no-backup storage; refresh tokens protected by Keystore where supported. | Server sees auth/session identifiers and opaque device binding state. | Revoke on logout, lost-device action, token expiry, suspected compromise, or account security event. | Account recovery may restore login ability but not content-key access. |
| Pairing secrets / short codes | Human-verifiable proof for one pairing/provisioning session. | Existing trusted device plus candidate device/watch for one bounded session. | Random high-entropy secret encoded as QR or short authentication string; ephemeral session keys per attempt. | Memory only; at most encrypted SQLCipher pending state for retry metadata without the secret. | Never visible. Server may relay opaque session ID, expiry, public ephemeral keys, and encrypted handshake messages. | Expires quickly; one accepted transcript consumes it. Duplicate/replayed attempts are rejected idempotently. | Not backed up and not recoverable. |

## Android-first bootstrap and secure storage

### First trusted Android phone

1. User installs the Android app and signs in or creates an account. Account authentication proves account ownership only; it does not recover E2EE content.
2. The app creates an installation ID, generates local database key material, creates Keystore wrapping material, and opens SQLCipher only after the key unwrap succeeds.
3. The app generates device identity and device key-agreement keys. When Android Keystore supports the key operation and policy, private keys should be non-exportable and hardware-backed if available. When the primitive is unavailable in Keystore, an audited crypto library may generate the key and store it wrapped by a Keystore key in no-backup storage.
4. The app registers only opaque device ID, public device keys, key IDs, and algorithm/version identifiers with the server. Capability details remain local/encrypted unless Paul later accepts a narrow privacy exception.
5. For a first private space, the trusted client generates a random space content key epoch and keeps plaintext key material only in trusted memory/SQLCipher. The server receives only opaque key ID/epoch metadata and encrypted payloads.

### Keystore capability handling

- Hardware-backed Keystore is preferred but not universal. Implementation must query platform capabilities and record a privacy-safe local diagnostic category such as `hardware_backed`, `software_backed`, or `unavailable`.
- Software-backed Keystore may be allowed for Phase 1 personal use if Paul accepts the trade-off. It must not be silently represented as hardware-backed.
- If Keystore is unavailable, locked, permanently invalidated, or lacks required authenticated encryption/signing support, startup fails with a typed key-storage state instead of overwriting local data or generating a second identity over existing encrypted state.
- Rooted, debug, emulator, or tampered devices are not automatically banned in this Proposed default, because self-hosters and development need testability. The app must show local risk state and may block production sync/key export on a later release-hardening decision.

### Authentication and invalidation policy

Recommended Phase 1 default:

- Require device secure lock screen before creating persistent E2EE-capable key material.
- Protect database-key unwrap and private-key use behind OS credential availability. Use user authentication gating for high-risk operations such as exporting/wrapping content keys to a newly approved device.
- Do not require biometric prompt for every background sync decrypt/encrypt operation in Phase 1, because offline reminders and sync would become unreliable. Require fresh user presence for provisioning a new device, viewing recovery material if later added, or changing key policy.
- Biometric enrollment/passcode changes must be handled explicitly. If a key was created with invalidation-on-biometric-change semantics, invalidation produces typed state `key_invalidated_by_credential_change`. The app must not delete encrypted data automatically.
- Reinstall, app-data clear, OS restore without no-backup keys, or Keystore reset creates a new installation. Existing server inventory may retain the old device as stale until revoked; the restored app cannot decrypt old local data without future recovery.

### Android backup exclusions

Until encrypted recovery/key backup is accepted, Android backup must be disabled for protected stores or explicitly exclude at least:

- SQLCipher database, WAL, journal, schema/export sidecars, and FTS/search sidecars;
- app-private files containing wrapped database keys, wrapped device keys, nonce ledgers, pending pairing state, or encrypted content-key caches;
- SharedPreferences/DataStore values that could contain token material, key aliases tied to encrypted blobs, or sensitive capability state;
- logs, crash breadcrumbs, screenshots, notification caches, attachment caches, and test artifacts containing protected content.

Backups of server PostgreSQL/object storage may contain ciphertext and allowed operational metadata only. They must not create a server-side content-key recovery path.

## Minimum Phase 1 WearOS provisioning

Phase 1 WearOS support is companion-mediated. A watch is trusted only after a currently trusted Android phone provisions it.

1. The user starts "Add watch" on the Android phone while unlocked and authenticated for provisioning.
2. The watch displays a pairing request or receives a platform companion channel established with the phone. The user confirms that the visible watch is the intended device.
3. Phone and watch create ephemeral key-agreement keys for this provisioning transcript. If a platform companion channel is used, application-level transcript keys are still used so server/cloud relay compromise cannot read provisioning material.
4. The phone verifies the watch's candidate public key and a short authentication string or QR-derived challenge shown across both devices.
5. The phone sends the minimum encrypted provisioning bundle needed for the watch subset: watch device ID, watch public keys, wrapped current space content key(s) for selected spaces, sync cursor bootstrap if required, and capability policy. The bundle is AEAD-protected with ADR-0004-compatible primitives and AAD binding account ID, phone device ID, watch candidate device ID, key epoch, protocol/suite version, nonce, expiry, and session ID.
6. The watch acknowledges by signing or MACing a key-receipt statement bound to the same transcript. The phone records the watch as trusted only after that acknowledgement.
7. Upload/register steps are idempotent. Retrying a failed transfer reuses the same pending provisioning session and either returns the original accepted watch registration or rejects conflicting reuse as a typed error.
8. If transfer fails, expires, is cancelled, or the watch factory-resets, the session moves to a failure state and the phone must start a new provisioning session. Partial sessions do not leave reusable plaintext keys on the watch.
9. Revoking the watch stops future sync/key delivery and should cause later key epochs to omit the watch. It cannot erase plaintext or old keys already retained by a compromised watch.

Standalone watch provisioning, independent watch account recovery, iOS/macOS, Apple Watch, browser clients, and device-to-device flows not mediated by a trusted Android phone are future decisions.

## New-device approval and pairing

### Actors

- Existing trusted device: already holds valid device identity and the relevant space content keys.
- Candidate device: freshly installed app with its own generated device ID and public keys but no content keys.
- Server: untrusted relay and inventory store. It authenticates accounts/devices, enforces expiry/idempotency/rate limits, stores opaque public keys and encrypted messages, but never learns private keys, plaintext content/database keys, or pairing secrets.

### Recommended default flow

1. Candidate signs in to the account. The server records it as `candidate_pending_user_approval` with opaque device ID, public identity key, public agreement key, key algorithm/version, and expiry. It receives no content keys.
2. Existing trusted device shows a local approval prompt after sync: "Approve a new device?" User-facing device name/platform details stay local or encrypted; the server-visible event is opaque.
3. Candidate and existing device exchange ephemeral X25519 public keys through a server-relayed session or QR/nearby channel. The pairing session has a random opaque session ID, short expiry, and single-use nonce/counter state.
4. Both devices compute a transcript hash over protocol version, suite ID, session ID, account ID or account-scoped opaque ID, approving device ID/public keys, candidate device ID/public keys, ephemeral public keys, expiry, requested key epochs, and server relay nonce(s).
5. The user verifies a short authentication string or QR challenge derived from the transcript hash on both devices. Approval requires explicit user confirmation on the existing trusted device.
6. The approving device encrypts a provisioning bundle to the candidate using an HKDF-SHA-256-derived AEAD key and XChaCha20-Poly1305. AAD binds the transcript hash, key epoch(s), content-key IDs, approving/candidate device IDs, session ID, expiry, suite ID, and purpose `new_device_provisioning_v1`.
7. The candidate verifies transcript binding, expiry, approving-device signature, and AEAD authentication before storing wrapped keys. It then sends a signed key-receipt acknowledgement bound to the transcript hash.
8. The approving device and server mark the candidate `trusted_active` only after acknowledgement. Duplicate retries with the identical transcript return the accepted result; conflicting reuse of a session ID, nonce, or candidate key is rejected.

### MITM, replay, and malicious-server resistance

- The short authentication string/QR challenge is derived from the transcript, so a relay that substitutes keys changes the user-visible verification value.
- Ephemeral key agreement gives forward secrecy for the provisioning transfer if static keys are later compromised, subject to implementation quality and endpoint compromise limits.
- Existing and candidate device public keys are included in signed transcript material, preventing the server from rebinding an encrypted bundle to another device without detection.
- Expiry, nonce/counter state, and one-time session consumption reject replayed provisioning messages.
- A malicious or self-hosted server can deny service, hide devices, delay messages, replay expired data, or present stale inventory, but it cannot decrypt provisioning bundles or forge user-verified transcript approval without compromising an endpoint or tricking the user into accepting a mismatched code.

## Device inventory and lifecycle

### Server-visible inventory fields

Allowed server-visible fields are limited to:

- opaque account ID / membership ID needed for auth;
- opaque device ID;
- device identity public key and key ID;
- device agreement public key and key ID;
- provisioning protocol version and crypto suite ID;
- key epoch/content-key IDs for routing encrypted wrapped keys, never plaintext keys;
- lifecycle state, created/last-seen/stale/revoked timestamps, and revocation reason category;
- opaque pending pairing session ID, expiry, retry/idempotency key, and typed error category;
- generic push/wake routing token where needed by ADR-0003.

Not allowed as plaintext server metadata in Phase 1:

- user-facing device names such as "Paul's Pixel";
- detailed platform model, OS patch level, serial number, advertising ID, hardware attestation fingerprint, IP-derived location, Bluetooth address, or capability fingerprint;
- task/list/project/reminder/attachment/object semantics;
- private keys, database keys, content keys, wrapping keys, pairing secrets, recovery secrets, decrypted diagnostics, or plaintext support bundles.

Privacy exception proposed for Paul: a coarse server-visible client class (`android_phone`, `wearos_companion`, `future_other`) may simplify routing and abuse/debug support, but it leaks platform information. Recommended default is not to store it in Phase 1; derive routing from opaque capability tokens or encrypted/local inventory unless review decides the operational value justifies the leak.

### Lifecycle states

| State | Meaning | Allowed transitions |
| --- | --- | --- |
| `local_unregistered` | Local install has generated local storage material but no server device record. | `candidate_pending_user_approval`, `reset_local_only` |
| `candidate_pending_user_approval` | Device authenticated to account but has no content keys. | `provisioning_in_progress`, `expired`, `revoked` |
| `provisioning_in_progress` | Existing trusted device/watch phone is transferring wrapped keys under one transcript. | `trusted_active`, `provisioning_failed`, `expired`, `revoked` |
| `trusted_active` | Device may authenticate, sync, receive future wrapped keys, and upload encrypted operations within membership policy. | `stale_suspected`, `revoked`, `key_rotation_required` |
| `stale_suspected` | Device has not checked in within a policy window or missed key epochs. | `trusted_active`, `revoked`, `expired` |
| `key_rotation_required` | Device remains known but must receive or prove possession of current epoch before full sync. | `trusted_active`, `revoked` |
| `revoked` | Device is no longer authorised for future sync or key delivery. | Terminal except audit/retention updates. |
| `expired` | Pending candidate/session expired without trust. | Terminal; user can start a new session. |
| `provisioning_failed` | Attempt failed without accepted acknowledgement. | New provisioning session or `revoked`. |
| `reset_local_only` | Local app reset/reinstall detected without matching private key. | New candidate registration. |

### Stale devices and lost-device revocation

Recommended default:

- Mark devices stale after 30 days without successful sync/key receipt, but do not revoke automatically in Phase 1.
- Show stale devices in local encrypted inventory and require explicit user action to revoke.
- Revocation immediately stops server authorisation for sync and future wrapped-key delivery.
- Future key epochs must omit revoked devices. Security-sensitive spaces may require client re-encryption under a later sharing/revocation ADR.
- Revocation cannot erase plaintext, screenshots, exports, old keys, notification text, or cached content already retained by the lost/compromised device.

## Provisioning versus recovery

Provisioning adds a device while at least one trusted device or accepted recovery mechanism can unwrap content keys. Recovery restores access after authorised devices are unavailable.

Phase 1 recovery posture:

- Account password reset or auth recovery restores login only. It must not grant space content keys.
- No server escrow, plaintext key backup, password-derived content key, or operator recovery is approved.
- Android cloud backup remains excluded for protected key and database material.
- If all trusted devices and future recovery material are lost, encrypted content may be permanently unrecoverable.
- Future recovery/key-backup work must choose a design explicitly, such as user-held recovery code, encrypted recovery key backup, social recovery, hardware-backed passkey recovery, or no recovery. Each option needs separate UX, threat model, rate-limit, lockout, and rotation review.

## Failure states and typed errors

Implementations should expose privacy-safe typed states instead of raw exceptions:

| Code | Meaning | Safe handling |
| --- | --- | --- |
| `keystore_unavailable` | Platform key service missing/locked/unusable. | Prompt for unlock or report unsupported device; do not overwrite data. |
| `keystore_software_backed` | Only software-backed protection is available. | Local warning/capability state; server stores no detailed fingerprint. |
| `key_invalidated_by_credential_change` | OS credential/biometric change invalidated a required key. | Show recovery/reset options only where cryptographically possible. |
| `db_key_unwrap_failed` | Wrapped SQLCipher key cannot authenticate/decrypt. | Stop opening DB; preserve files for possible future recovery. |
| `device_private_key_unavailable` | Signing/agreement key cannot be used. | Block provisioning/sync auth until resolved or re-provisioned. |
| `pairing_expired` | Session expiry elapsed. | Delete pending secrets and start a new session. |
| `pairing_transcript_mismatch` | Verification code/QR or transcript hash does not match. | Abort and warn user; no key delivery. |
| `pairing_replay_rejected` | Session/nonce/counter already consumed or stale. | Return idempotent accepted result for identical retry; reject conflicts. |
| `provisioning_ack_missing` | Candidate did not acknowledge key receipt. | Keep candidate pending/failed; do not mark trusted. |
| `revoked_device` | Device attempted auth/sync/key receipt after revocation. | Deny future access with opaque error and audit event. |
| `unsupported_provisioning_version` | Protocol/suite not supported. | Fail closed before decryption/key storage. |
| `recovery_not_configured` | User attempts recovery before a design exists. | Explain unrecoverability honestly without suggesting server reset can decrypt. |

## Privacy-safe logging and telemetry

- Phase 1 diagnostics remain local-only unless a later accepted design allows remote support export/telemetry.
- Server logs may include opaque account/device/session IDs, request IDs, lifecycle state, expiry, typed error code, redacted counts, and coarse timestamps needed for abuse prevention and audit.
- Logs, metrics, CI artifacts, support bundles, push payloads, and object metadata must not include user-facing device names, platform fingerprints, protected task semantics, plaintext key material, pairing codes/secrets, transcript shared secrets, recovery secrets, or decrypted content.
- Client local diagnostics may show capability categories and typed errors, but user-consented export remains a future design.

## Architecture-level API and event requirements

Future API/schema tasks must preserve these requirements:

- `POST /devices/candidates` registers a candidate with opaque device ID and public keys only; no content keys or pairing secrets.
- `POST /device-pairing-sessions` creates an opaque, expiring, rate-limited session with idempotency key and relay metadata.
- `POST /device-pairing-sessions/{id}/messages` relays encrypted handshake/provisioning messages without inspecting decrypted payloads.
- `POST /device-pairing-sessions/{id}/ack` records signed key-receipt acknowledgement and returns idempotent accepted/conflict state.
- `GET /devices` returns privacy-safe inventory; user-facing names and detailed platform data are encrypted/local.
- `POST /devices/{id}/revoke` marks a device revoked for future auth/sync/key delivery and emits an audit event.
- Sync/key APIs must check device lifecycle state and key epoch authorisation before returning encrypted operations or wrapped keys.
- Events/audit records use opaque IDs and typed categories: candidate registered, pairing started, pairing expired, provisioning acknowledged, device trusted, device stale, device revoked, key epoch advanced.

Endpoint names are illustrative. Implementation tasks may choose final paths and schema names, but not weaken the boundaries above without a new accepted decision.

## Deterministic and synthetic test strategy

Future implementation should add deterministic tests with synthetic values only:

- Keystore abstraction tests for hardware-backed, software-backed, unavailable, locked, invalidated, corrupted-wrapper, reinstall/restore, and credential-change states.
- SQLCipher open tests proving existing DB is not overwritten when key unwrap fails.
- Android backup policy tests or manifest/static checks proving protected paths are excluded.
- Pairing transcript vector tests covering canonical transcript hash, short authentication string derivation, AAD binding, expiry, wrong-device substitution, wrong-approver substitution, replayed message, duplicate idempotent retry, and conflicting session reuse.
- Phone-to-watch provisioning tests covering explicit user approval, authenticated encrypted bundle, acknowledgement, retry, cancellation, expiry, partial transfer cleanup, and revocation.
- Server contract tests proving schemas/logs/events accept only opaque inventory metadata and never content/database/private keys, pairing secrets, plaintext device names, or protected task semantics.
- Revocation tests proving future sync/key delivery fails while documentation/UI states that old plaintext cannot be clawed back.
- Protocol compatibility tests using ADR-0004 suite/version IDs and fail-closed behaviour for unsupported/deprecated provisioning versions.

## Threat cases

| Threat | Mitigation | Residual risk |
| --- | --- | --- |
| Curious or malicious server/self-host admin tries to read keys. | Server receives public keys, opaque IDs, ciphertext, wrapped-key ciphertext, and typed states only. Pairing secrets/private/content/database keys never leave trusted devices. | Server can deny service, hide devices, delay revocation, and observe allowed metadata/timing/sizes. |
| Server substitutes a candidate key during pairing. | Transcript hash, device public keys, and user-verified SAS/QR bind the session. | User can still be socially engineered to approve a mismatched code. |
| Replay of old provisioning bundle. | Expiry, nonce/counter state, session consumption, transcript binding, and key epoch checks. | Compromised endpoint with retained old keys may still read old content. |
| Lost or stolen unlocked device. | Keystore/SQLCipher protect at rest; remote revocation stops future sync/key delivery. | If attacker can unlock or compromise the OS, already-local plaintext/keys may be exposed. |
| Biometric/passcode change invalidates keys. | Typed invalidation state; no destructive overwrite. | Without recovery, data may be unavailable. |
| Android backup restores encrypted blobs without Keystore keys. | No-backup exclusions; restored app registers as new device. | Misconfigured backup could strand unusable ciphertext; tests must catch this. |
| Compromised client build exfiltrates keys. | Future supply-chain/release hardening and synthetic CI fixtures. | E2EE cannot protect against malicious authorised clients. |
| Detailed device metadata fingerprints users. | Store only opaque IDs/public keys/coarse lifecycle state by default. | Coarse timings/last-seen and optional future capability exceptions still leak some operational metadata. |

## Migration and versioning

- Provisioning protocol version starts at `device_provisioning_v1` and must include ADR-0004 suite ID `TM-E2EE-v1-XCHACHA20POLY1305-HKDF-SHA256` or an explicitly accepted successor.
- Device records store key algorithm IDs and validity intervals so later migrations can add successor keys without reusing old identifiers.
- Unknown, deprecated, or unsupported provisioning versions fail closed before accepting wrapped keys.
- Schema/API migrations must be additive while clients of older accepted versions exist; do not remove old public keys/key IDs until no retained old key epoch requires them.
- Revoked device records remain as audit/denial state until a later retention policy says they can be pruned.

## Alternatives considered

### Server escrow of content keys

Rejected. It violates ADR-0001 and ADR-0003 because the server/operator could decrypt protected content or recover it through account reset.

### Password-derived content-key recovery in Phase 1

Deferred. It may improve recoverability but adds online/offline guessing, lockout, KDF parameter, password reset, UX, and rotation risks that need a separate recovery ADR.

### Require hardware-backed keys for all users

Deferred/rejected as a universal Phase 1 gate. Hardware backing is stronger but not universal across Android/WearOS devices and development environments. The recommended default is to prefer hardware-backed storage, surface capability honestly, and decide later whether production sync blocks weaker devices.

### Store plaintext device names/platform details on the server

Rejected as the default. Human-readable names and detailed platform/capability fingerprints are unnecessary for encrypted routing and can identify a user. If Paul accepts a future support/usability exception, it must be explicit, narrow, and documented.

### Standalone WearOS provisioning in Phase 1

Deferred. Companion-mediated provisioning keeps Phase 1 bounded and avoids designing independent watch recovery, input, display, and secure-channel UX before the Android phone flow is accepted.

## Decisions requested from Paul

| Decision | Recommended default | Trade-offs |
| --- | --- | --- |
| Permit software-backed Keystore devices for Phase 1 personal/self-host use? | Permit with local warning and no claim of hardware backing. | Broader compatibility and easier development; weaker protection on compromised/rooted devices. |
| Require fresh user authentication for every sync key use or only high-risk key export/provisioning? | Require fresh user presence for provisioning/recovery/key-policy changes, not every background sync. | Better usability/offline reminders; a currently unlocked compromised device has more opportunity. |
| Store coarse plaintext client class on server? | Do not store by default; use opaque routing/capability tokens and encrypted/local labels. | Stronger privacy; harder support/routing/debugging. |
| Auto-revoke stale devices? | Mark stale after 30 days but require explicit user revocation. | Avoids locking out rarely used devices; stale devices remain eligible until user acts. |
| Block rooted/debug/emulator devices from production sync? | Warn locally now; defer hard block to release-hardening decision. | Keeps development/self-host testing possible; weaker assurance for production unless later tightened. |
| Phase 1 recovery posture? | No content recovery unless an authorised device remains; account reset does not recover content keys. | Honest E2EE boundary; users can permanently lose data without later recovery design. |
| WearOS scope? | Companion-mediated watch only; no standalone watch/Apple clients. | Bounded Android-first delivery; future platforms need additional ADR/spec work. |

## Acceptance mapping

| Task criterion | Specification response |
| --- | --- |
| Key purposes and boundaries | Key/identifier table defines purpose, owner, generation, storage, server visibility, rotation/revocation, and backup/recovery. |
| Android-first bootstrap and secure storage | Android bootstrap, Keystore capability, auth/invalidation, reinstall/restore/rooted posture, and backup exclusions are specified without claiming universal hardware backing. |
| Minimum WearOS flow | Companion-mediated phone-to-watch provisioning covers explicit trust, transcript protection, retries, failure, revocation, and future standalone boundaries. |
| New-device approval | Pairing flow binds transcript, mutual authentication, expiry, MITM/replay resistance, duplicate/retry handling, malicious server assumptions, and ADR-0004-compatible primitives. |
| Inventory and lifecycle | Inventory metadata, lifecycle states, stale handling, lost-device revocation, and key-epoch consequences are specified with honest revocation limitations. |
| Server-visible metadata | Allowed and prohibited metadata follow ADR-0003 and propose only one explicit privacy exception for Paul. |
| Recovery separation | Provisioning and recovery are separated; Phase 1 does not promise recoverability. |
| Failure/logging/API/tests/threats | Typed errors, privacy-safe logs, API/event requirements, synthetic test strategy, threat cases, limitations, alternatives, migration/versioning, and non-goals are included. |
| Unresolved choices | Decisions requested from Paul lists recommended defaults and trade-offs. |
