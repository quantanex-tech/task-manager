# ADR-0003 — Phase 1 privacy threat model and metadata boundary

Status: Proposed
Date: 2026-08-28
Decision owner: Paul
Related task: Notion page `3b0319db76c881d39578f6cdbdaa6a77`
Specification: [`docs/security/privacy-threat-model.md`](../security/privacy-threat-model.md)

## Context

ADR-0001 makes end-to-end encryption mandatory before any usable release stores or synchronises real task data. ADR-0002 requires Android and WearOS local persistence to keep protected content in SQLCipher-encrypted storage and out of plaintext preferences, logs, caches, analytics, backups, and test artifacts.

The next Phase 1 architecture/API/security work needs a field-level privacy threat model and metadata boundary. Without one, server schemas, sync APIs, notifications, billing, diagnostics, support exports, CI fixtures, and self-hosting docs could accidentally encode plaintext task semantics or server-visible metadata that weakens the accepted E2EE promise.

This ADR is Proposed only. It records the candidate decision for technical review and Paul’s later human/business acceptance; it does not mark the Notion task accepted, implemented, deployed, or merged.

## Decision

Adopt `docs/security/privacy-threat-model.md` as the Phase 1 privacy threat-model specification and metadata boundary once accepted.

The service is not trusted with protected content or content-decryption keys. Android and WearOS clients are trusted to decrypt user content locally after key availability; the Docker-first service, PostgreSQL, S3-compatible object storage, network, CI, backups, notification providers, diagnostics/support surfaces, hosted operators, and self-hosted operators are not trusted with plaintext protected content.

Protected content includes task and sub-task titles, descriptions, project/list and label names, comments, reminder details, recurrence expressions, attachment bytes/names/previews, user search terms, and key material. The following delivery-owned fields also remain encrypted and must not appear in plaintext server metadata, logs, push payloads, telemetry, unencrypted local storage, CI artifacts, or support exports: object type, priority, due-date presence, completion state, and coarse reminder existence.

Server-visible metadata is limited to operationally necessary account, device, membership, billing, audit, storage, routing, encrypted-object, and sync-ordering metadata. Every server-visible field must have a minimal-purpose justification and a retention/deletion expectation before implementation. For current architecture scope, tombstones, audit metadata, billing records, object bytes, and server sequence history are treated as persistent; detailed retention/deletion windows remain deferred production/dogfood decisions and are not finalised here.

Phase 1 diagnostics are local-only: no diagnostic or telemetry data leaves the device. Future support export or remote telemetry requires a separate explicit design covering consent, redaction, storage, retention, and protected-content handling.

Member or device revocation prevents future access by stopping future sync authorisation and key delivery. It cannot erase data already decrypted, copied, screenshotted, exported, cached, or otherwise retained by a previously authorised client or human.

## Consequences

### Positive

- Later API, schema, sync, notification, attachment, billing, diagnostics, and CI work has a concrete field-level privacy boundary.
- The server can still authenticate users, authorise memberships, route encrypted objects, assign sync sequences, enforce quotas/rate limits, store encrypted attachments, and run billing without reading task content.
- Android/WearOS clients retain private local search, recurrence, reminder, ordering, and conflict semantics inside encrypted local storage.
- The threat model makes hosted and self-hosted operator limitations explicit: operators can see allowed operational metadata and ciphertext, not protected content.
- Security/privacy test cases are derivable from the specification and can become future contract, log-redaction, push-payload, backup, and fixture checks.

### Negative / costs

- The server cannot filter, sort, search, schedule meaningful reminders, inspect object types, or resolve semantic conflicts using plaintext task data.
- Generic push wake-ups and client-side reminder evaluation are required; server-side semantic reminder scheduling is out of scope until a privacy-preserving design exists.
- Some operational metadata remains visible, including account identity, membership relationships, billing state, ciphertext sizes, timings, request IDs, server sequences, and storage lifecycle metadata.
- Client implementations carry more responsibility for search, recurrence, reminders, conflict resolution, and privacy-safe diagnostics.
- Support and debugging are harder because Phase 1 diagnostics cannot leave the device and support exports are not yet designed.

## Non-goals

- This ADR does not select final cryptographic algorithms, key hierarchy, envelope schema, key rotation cadence, or recovery protocol.
- This ADR does not implement code, API endpoints, migrations, clients, telemetry, support export, CI checks, deployment, or production retention workflows.
- This ADR does not approve plaintext server storage, server-managed content keys, semantic server indexes, server-readable notification schedules, or decrypted support bundles.
- This ADR does not provide anonymity from all network or operational observers; sizes, timings, account metadata, billing metadata, and membership relationships remain visible where operationally necessary.
- This ADR does not mark Paul’s human/business acceptance complete.

## Residual risks and blockers

- A malicious or compromised authorised client can read and exfiltrate plaintext after decryption. E2EE protects against the service and storage operators, not against a malicious trusted endpoint.
- Revocation cannot claw back content a member/device already decrypted or copied.
- Size, timing, account, membership, billing, storage, audit, and sync-sequence metadata remain observable to operators and attackers who compromise those systems.
- Losing all authorised devices and recovery material may make encrypted content permanently unrecoverable because account reset is separate from content-key recovery.
- Detailed retention/deletion windows for tombstones, audit metadata, billing records, object bytes, and server sequence history are deferred and must be decided before dogfood/production lifecycle promises.
- Backup/recovery, encrypted sync conflict resolution, device provisioning, sharing/key rotation, support export, telemetry, supply-chain hardening, and release provenance remain Phase 1 blockers before real synced user data can be trusted.

## Required implementation rules after acceptance

1. New server API, event, job, schema, log, and push-payload fields must be privacy-classified before implementation.
2. Server code must reject or avoid plaintext object type, priority, due-date presence, completion state, reminder existence, names, comments, search terms, recurrence expressions, attachment filenames/previews, and decrypted task content.
3. Push notifications must carry generic wake/sync hints only until a privacy-preserving notification design is accepted.
4. CI and tests must use synthetic data or deterministic encrypted vectors, never real plaintext protected user content.
5. Logs, crash reports, support artifacts, and diagnostics must use opaque IDs and redacted typed error categories only.
6. Any future remote telemetry or support export must be explicitly designed and accepted before data leaves a device.

## Acceptance and review state

This ADR remains Proposed. Technical review may approve the documentation candidate, but Paul’s human/business acceptance remains a separate gate before the decision becomes Accepted or downstream work relies on it as final.
