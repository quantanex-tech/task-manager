# ADR-0007 — Private search, recurrence, reminders, and notifications

Status: Accepted
Date: 2026-08-30
Decision owner: Paul
Related task: Notion page [`3b0319db-76c8-81b0-9a4d-cc650af6a3d4`](https://app.notion.com/p/architecture-keep-search-recurrence-reminders-and-notifications-private-3b0319db76c881b09a4dcc650af6a3d4)
Acceptance evidence: Paul moved the source task to Ready and authorised the ADR write-up in Notion comment `3cc319db-76c8-8094-adb2-001d349c7085`.
Depends on: [ADR-0001](0001-phase-1-e2ee-mandatory.md), [ADR-0002](0002-encrypted-client-sqlite-persistence.md), [ADR-0003](0003-phase-1-privacy-threat-model-and-metadata-boundary.md), [ADR-0004](0004-phase-1-e2ee-protocol-and-encrypted-entity-envelope.md), [ADR-0005](0005-phase-1-encrypted-offline-sync-and-conflict-resolution.md), [ADR-0006](0006-phase-1-device-identity-provisioning-and-secure-key-storage.md)

## Context

Todoist-like Phase 1 workflows need search, saved views, recurrence, reminders, notification actions, and Android/WearOS presentation. ADR-0001 makes end-to-end encryption mandatory before any usable release stores or synchronises real task data. ADR-0002 keeps Android/WearOS protected local state in encrypted SQLite. ADR-0003 defines reminder details, recurrence expressions, search terms, due-date presence, completion state, object semantics, diagnostics, logs, telemetry, push payloads, and support/export surfaces as protected unless explicitly allowed. ADR-0004 defines the encrypted entity envelope and server-visible ciphertext boundary. ADR-0005 defines encrypted, idempotent offline sync and client-side deterministic convergence. ADR-0006 defines trusted device/key availability, local-first encrypted bootstrap, Android Keystore boundaries, WearOS companion provisioning, and device revocation limits.

The source Notion task records Paul’s accepted Phase 1 decisions for private search, recurrence, reminders, Android exact-alarm behaviour, Android/WearOS reminder presentation, content-free server wake-ups, and local notification rendering. This ADR records those decisions as repository governance only. It does not implement production code, schemas, APIs, Android/WearOS UI, alarm scheduling, push delivery, cryptography, migrations, telemetry, recovery, or release behaviour.

## Decision

Adopt a client-side private search, recurrence, reminder, and notification architecture for Phase 1.

1. Search, filters, and saved views execute on trusted clients against decrypted local state. Derived search/filter indexes are encrypted at rest, disposable, and rebuildable from encrypted canonical entities.
2. Recurrence rules, occurrence state, reminder due times, reminder schedules, snooze/repeat state, and scheduler recovery state remain protected client data. Eligible trusted clients evaluate recurrence and schedule reminders locally from decrypted synced state.
3. Android users can choose an exact reminder time. When Android exact-alarm capability is unavailable, restricted, revoked, or cannot be preserved after recovery events, the client must surface a clear degraded state and actionable guidance. Silent fallback to best-effort scheduling is not acceptable.
4. When an Android phone and WearOS device are both eligible and hold the required key, each device presents one local reminder. A disconnected eligible keyed device continues evaluating and presenting local reminders from its local encrypted state. A device without the required key must not trigger protected reminders. Shared-state actions, recurrence advancement, and sync mutations remain encrypted and idempotent.
5. Server push may be used only as a content-free `data changed` wake-up for sync. It carries no task identifier, reminder time, due timestamp, schedule, recurrence detail, query, or notification text. Timed or coarsened wake hints are prohibited unless measured delivery reliability fails an explicit requirement and a later privacy/ADR review approves the metadata leakage.
6. Meaningful notification content is decrypted and rendered locally by a trusted client that has the required key. Android/WearOS notification and lock-screen privacy controls remain authoritative. Generic/private presentation is required when content is suppressed, redacted, unavailable, or undecryptable.
7. Notification actions such as complete, snooze, dismiss, and open enter the normal encrypted, idempotent local mutation outbox for later sync. Logs, metrics, crash reports, diagnostics, CI artifacts, support exports, push payloads, and analytics must not contain protected content, queries, keys, tokens, exact private schedules, or notification text.

## Trust and server-visible boundary

Trusted Android and WearOS clients may decrypt local state after required keys are available and may maintain local derived indexes, schedules, recurrence state, notification rendering state, and local outbox records inside encrypted storage.

The server is limited to ADR-0003/ADR-0004/ADR-0005 operational metadata and opaque encrypted sync objects. It may authenticate, authorise, route, store ciphertext, assign server sequence, deduplicate retries, return ordered changes, and send content-free wake-ups. It must not receive plaintext search terms, local index tokens, filter predicates over protected fields, recurrence expressions, occurrence state, reminder existence/details, due timestamps, exact/coarsened private schedules, notification title/body/action text, object semantics, content keys, local database keys, pairing secrets, device-private keys, or decrypted diagnostics.

Generic wake-ups are not a back door for scheduling. A wake-up can tell a client that encrypted changes may be available; it cannot encode when a private reminder is due, which task changed, what recurrence rule applies, or what notification should say.

## Android and WearOS behaviour

Android exact reminder times are a product requirement. If the OS grants exact-alarm capability and the device is powered on, the client schedules the user-selected instant. Device-off states, OEM limits, OS restrictions, revoked permissions, battery policy, app hibernation, missing keys, corrupted local state, and unsupported platform capabilities are explicit degraded or unrecoverable states, not reasons to silently downgrade the user’s exactness expectation.

WearOS Phase 1 reminder behaviour is companion-mediated and key-bound as accepted by ADR-0006. A watch may present reminders only when it is an eligible trusted device with the required key material and sufficient decrypted local state. Phone and watch notification presentation is per eligible device: if both are eligible, both present the due reminder once locally. Deduplication and leases must protect shared encrypted mutations, recurrence advancement, and sync convergence; they must not suppress an eligible device’s local notification merely because another eligible device is online.

Notification display must respect platform controls. If Android or WearOS suppresses, hides, or redacts notification content on the lock screen or for a channel/user preference, the app uses a generic/private notification that reveals no protected content. If a client cannot decrypt or locate the protected reminder payload, it fails closed to a generic/private prompt rather than displaying stale or guessed content.

## Offline and recovery implications

Search, filters, saved views, recurrence evaluation, reminder scheduling, and notification action capture continue from encrypted local state while offline. Derived indexes and schedules are disposable: deleting them and rebuilding from encrypted canonical local entities should produce equivalent behaviour once keys and schema support are available.

Clients must reschedule or explicitly mark reminders after reboot, app update, time-zone change, daylight-saving transition, key migration, schema migration, sync catch-up, and scheduler capability changes. Recovery must avoid plaintext fallback and must not leak reminder details through logs, diagnostics, push payloads, or server metadata.

Queued notification actions sync later through ADR-0005’s encrypted operation outbox. Replayed actions must be idempotent. Long-offline clients must reconcile pulled encrypted operations before resurrecting stale reminders, recurrence occurrences, or shared-state changes.

## Consequences and limitations

### Positive

- Private search, filters, recurrence, reminders, and meaningful notifications remain compatible with ADR-0001’s E2EE promise.
- The server can stay operationally simple and privacy-preserving: sync encrypted changes and optionally wake clients without understanding task semantics.
- Android and WearOS users get offline-capable local reminder behaviour when their device is eligible and keyed.
- Derived indexes and schedules can be rebuilt after key, schema, sync, or app migrations without treating derived state as canonical.
- Platform notification privacy settings remain authoritative rather than being bypassed by app-specific rendering.

### Costs and limitations

- Clients carry more complexity for indexing, recurrence, exact-alarm capability handling, scheduler recovery, local notification rendering, outbox idempotency, and conflict handling.
- The server cannot provide plaintext search, semantic saved-view filtering, reminder scheduling, recurrence expansion, notification text generation, or support debugging over protected content.
- Delivery reliability depends on client scheduling, OS background execution policy, local key availability, and timely encrypted sync. Content-free wake-ups can improve sync freshness but cannot act as private schedules.
- Device revocation blocks future sync authorisation and future key delivery, but cannot erase plaintext, old keys, screenshots, notifications, exports, or cached content already held by a previously authorised device or human.
- Losing all authorised devices and recovery material can make protected reminder/search/recurrence data permanently unrecoverable, consistent with ADR-0001 and ADR-0006.

## Rejected alternatives

### Server-side plaintext search, filtering, recurrence, or reminder scheduling

Rejected because it would expose protected content, search terms, recurrence expressions, due/reminder schedules, object semantics, and notification meaning to the service, contradicting ADR-0001 and ADR-0003.

### Server-visible exact or coarsened timed wake hints in Phase 1

Rejected for Phase 1 because reminder due times and schedules are protected data. Timed/coarsened wake hints may be reconsidered only if measured reliability fails an explicit requirement and a later privacy/ADR review accepts the leakage.

### Silent best-effort fallback for Android exact reminders

Rejected because Paul accepted exact user-selected reminder times as a product requirement. If the platform cannot provide exact-alarm capability, users must see a clear degraded state and guidance.

### Suppressing one eligible device’s local reminder when another eligible device is online

Rejected because accepted behaviour is per eligible keyed device presentation. Deduplication belongs to shared-state mutation and recurrence advancement, not local presentation suppression.

### Server-generated meaningful notification text

Rejected because meaningful notification content is protected and must be decrypted/rendered locally. Server push payloads and notification-provider payloads must not carry task titles, details, queries, schedules, or recurrence semantics.

## Follow-up implementation boundaries

This ADR authorises documentation of the accepted architecture only. Future implementation tasks must stay within these boundaries unless a later accepted ADR supersedes them:

- Define and test deterministic recurrence, reminder, snooze, completion, dismiss, stale-reminder, delete, and reschedule reconciliation for conflicting disconnected device actions, consistent with ADR-0005 client-side deterministic convergence. Do not introduce server-visible schedules or semantic operation inspection to solve this.
- Add synthetic tests for local search-index rebuild, recurrence across time zones/daylight-saving transitions, scheduler recovery after reboot/app update/time-zone change/sync catch-up, exact-alarm capability degradation, lock-screen privacy modes, logs/metrics/crash-report redaction, push-payload redaction, and encrypted outbox idempotency.
- Keep Android/WearOS implementation behind client repository/service boundaries so UI code does not write plaintext side channels or bypass platform notification controls.
- Treat support export, remote telemetry, richer wake hints, standalone WearOS provisioning, iOS/browser/desktop notification behaviour, external calendar integration, team mentions, recovery/key backup, and production release hardening as separate future decisions.

## Acceptance and verification consequences

Future work that claims compatibility with this ADR must prove at least:

1. Search and saved-view/filter execution can run offline on decrypted local state without any plaintext search API call.
2. Local indexes and schedules can be deleted and rebuilt from encrypted canonical entities.
3. Recurrence rules, occurrence state, reminder due times, schedules, and notification content never appear in server schema fields, API payloads, logs, metrics, crash reports, CI artifacts, support exports, push payloads, or analytics.
4. Android exact-alarm capability success and degraded states are explicit and testable; best-effort fallback is never silent.
5. Phone and WearOS devices that are both eligible and keyed each present one local reminder, while shared encrypted actions and recurrence advancement remain idempotent.
6. Devices without required key material do not trigger protected reminders and fail closed to generic/private states.
7. Notification actions enter the encrypted local outbox and remain safe to replay after app restart, offline use, retry, and sync catch-up.
8. Reboot, app update, time-zone change, daylight-saving transition, key migration, schema migration, prolonged offline use, and sync reconciliation do not leak protected content or resurrect stale reminder state.
9. Any proposal to add server-visible timed wake hints, semantic reminder metadata, remote telemetry, or support export content has a new privacy review and accepted ADR before implementation.

ADR-0007 is Accepted as of 2026-08-30. It does not modify or supersede ADR-0001 through ADR-0006 and does not mark any implementation, deployment, Notion completion, production cryptography, Android/WearOS feature, server API, schema, telemetry, or release task done.
