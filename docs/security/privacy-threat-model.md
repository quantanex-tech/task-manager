# Phase 1 privacy threat model and metadata boundary

Related ADR: [ADR-0003](../adr/0003-phase-1-privacy-threat-model-and-metadata-boundary.md)
Related Notion task: `3b0319db76c881d39578f6cdbdaa6a77`
Status: Phase 1 specification for review; ADR-0003 remains Proposed until Paul accepts it.

## Purpose

This specification defines what the Phase 1 end-to-end encryption design protects, which actors are untrusted, what the service may still observe, and which risks remain accepted or blocked. It is a documentation-only boundary for later Android, WearOS, server, sync, notification, diagnostics, backup, billing, and support work.

ADR-0001 is accepted: real user task data must be encrypted before it leaves a trusted client, and the service must not have content-decryption keys. ADR-0002 is accepted for local Android/WearOS persistence: protected data may be queryable inside SQLCipher-encrypted local SQLite, but must not be written to plaintext preferences, logs, caches, backups, analytics, crash breadcrumbs, CI artifacts, or server storage.

## Privacy classes

| Class | Meaning | Examples | Default log/telemetry rule |
| --- | --- | --- | --- |
| E2EE content | User content or semantic task state encrypted before leaving trusted clients. The service stores only ciphertext and opaque routing metadata. | Task titles/descriptions, project/list/label names, comments, reminder details, recurrence rules, priority, due-date presence, completion state, coarse reminder existence. | Never log or transmit as plaintext. Redact from crashes, analytics, server logs, CI artifacts, push payloads, support exports, and unencrypted storage. |
| Local encrypted derivative | Search indexes, sort/reminder derivatives, cached notification state, or recurrence calculations derived from E2EE content and kept only inside encrypted client storage. | SQLite FTS inside SQLCipher, local reminder queue, local search terms, local completion history. | Local diagnostics may report redacted counts/categories only. No remote telemetry in Phase 1. |
| Server-visible operational metadata | Minimal opaque metadata the server needs to authenticate, route, authorise, order encrypted operations, bill, rate-limit, retain, delete, or operate storage. | Account ID, device ID, space ID, membership role, opaque object ID, encrypted object size, sequence number, storage path, object ETag. | May appear in privacy-safe server logs only when needed, without protected content or semantic field names. Phase 1 telemetry is local-only, so do not export remotely. |
| Account/billing metadata | Plaintext information needed to run accounts, legal/commercial records, entitlements, abuse prevention, and invoices. | Email, auth provider subject, plan, entitlement flags, billing customer/subscription IDs. | Minimise and redact. Do not combine with E2EE content. Retention follows legal/business requirements and is a production decision. |
| Prohibited plaintext | Values that must not be stored or transmitted unencrypted outside trusted clients. | Key material, decrypted content, attachment previews, user search terms, plaintext fixture data copied from real users. | Forbidden in server metadata, logs, push payloads, telemetry, backups, unencrypted local files, CI artifacts, and support exports. |

## Plaintext-prohibited data

The following must never leave a trusted client as plaintext and must never appear in plaintext server metadata, logs, push payloads, analytics, telemetry, CI artifacts, support bundles, unencrypted local files, or object-store metadata:

- task and sub-task titles, descriptions, notes, bodies, markdown, and completion history;
- project, space, list, section, and label names;
- comments, comment attachment captions, activity text, and user-authored descriptions;
- reminder dates/times/details, recurrence expressions, notification text, priority, due-date presence, completion state, and coarse reminder existence;
- attachment bytes, names, thumbnails, previews, extracted text, MIME-derived semantic labels, and content hashes if they can identify content;
- user-entered search terms and local search index tokens;
- plaintext key material, recovery secrets, database keys, content keys, signing keys, SQLCipher passphrases, and decrypted sync keys;
- object type or semantic entity class where it reveals whether ciphertext represents a task, sub-task, comment, reminder, recurrence rule, label, list, or attachment.

## Data classification matrix

Allowed locations use these abbreviations: trusted client memory, SQLCipher local DB, Android Keystore/no-backup key wrapper metadata, encrypted sync payload, PostgreSQL, S3-compatible object store, local-only diagnostics, billing provider, CI. PostgreSQL and S3 may store ciphertext plus minimal operational metadata only.

| Entity | Core fields | Privacy class | Allowed storage/location | Server visibility | Log/telemetry rule | Rationale |
| --- | --- | --- | --- | --- | --- | --- |
| Accounts | Account ID, login email or auth subject, password reset/auth state, MFA/account recovery state, locale, coarse account timestamps | Account/billing metadata except secrets | Auth service tables in PostgreSQL; trusted clients; billing provider where needed | Account ID, email/auth subject, auth events, coarse timestamps | Redact email where possible; no task content; no remote telemetry in Phase 1 | Needed for authentication, account recovery, support identity, abuse prevention, and billing. Account reset must not grant content-key access. |
| Devices | Device ID, public device key ID, device display label, wrapped space keys, capability flags, last-seen timestamp, hardware-backed-key indicator | Device ID/key ID/capabilities are server-visible operational metadata; display label is E2EE content unless Paul explicitly accepts otherwise | Device secrets in Keystore/client; public keys and opaque IDs in PostgreSQL; wrapped keys as encrypted payloads | Opaque device ID, public key material, key version ID, membership, last-seen, hardware-backed capability flag | Never log private keys or display labels; local diagnostics may show capability categories only | Server must route encrypted keys and revoke future access, but must not learn human-readable device names or private keys. |
| Spaces/projects | Space/project ID, name, description, membership list, role, encrypted space key version, created/updated timestamps | Name/description are E2EE content; opaque ID, membership role, key version, timestamps are server-visible operational metadata | Names/descriptions in SQLCipher and encrypted sync; memberships/key versions in PostgreSQL; encrypted object storage for ciphertext | Opaque space ID, account membership, role, key version, created/updated order | No plaintext names in logs, CI, push, telemetry, or support exports | Server needs authorisation and routing. User-visible names reveal content and remain encrypted. |
| Lists | List ID, project/space parent, name, local order key, archived/deleted state | Name, order semantics, archived/deleted meaning are E2EE content; opaque ID/parent scope may be server-visible only as routing scope | SQLCipher; encrypted sync payload/object | Opaque object ID, scope, ciphertext size/version/sequence only | Never log name/order/archive state | Lists are user-authored organisation content. Server may store ciphertext but not list semantics. |
| Tasks | Task ID, parent/list ID, title, description, priority, due-date presence/details, completion state/time, order key, created/updated/deleted timestamps, encrypted payload version | Title/description/priority/due/completion/order/object type are E2EE content; opaque ID, space routing ID, ciphertext version, size, server sequence are server-visible operational metadata | SQLCipher; encrypted sync payload in PostgreSQL/S3; local encrypted search/reminder derivatives | Opaque encrypted object ID, owning space/account membership, ciphertext blob pointer, byte size, version/key ID, server sequence, coarse ingestion timestamp | Do not log title, priority, due/completion/reminder flags, order, object type, or decrypted timestamps; logs may use opaque IDs and request IDs | Task semantics are core protected data. Server needs only to store and order encrypted operations. |
| Sub-tasks | Sub-task ID, parent task relationship, title, description, completion state, order key | Same as tasks; parent-child semantics are E2EE content unless represented as opaque encrypted payload | SQLCipher and encrypted sync/object storage | Opaque object ID and encrypted payload metadata only | Never log parent semantics, title, status, order, or object type | A sub-task can reveal the user's plan and must receive the same protection as a task. |
| Labels | Label ID, name, color, assignment to tasks, sort/order | Label name/color/assignments are E2EE content | SQLCipher and encrypted sync/object storage | Opaque object IDs and ciphertext metadata only | Never log plaintext label values or assignments | Labels are user-authored taxonomy and reveal content. |
| Comments | Comment ID, author membership ID, body, created/edited/deleted timestamps, attachment references | Body, attachment captions, edit/delete semantics are E2EE content; opaque author/account membership ID may be server-visible for auth/audit | SQLCipher and encrypted sync/object storage | Opaque object ID, account/member routing ID, ciphertext version/size/sequence | No body text, snippets, mention terms, or edit summaries in logs/support/CI | Comments often contain the highest-sensitivity plaintext. |
| Reminders | Reminder ID, linked task/object ID, due timestamp, timezone, notification text, snooze state, delivery state | Reminder details, due-date presence, coarse reminder existence, snooze/delivery state are E2EE content/local encrypted derivatives | SQLCipher and local client scheduler; generic push wake token if needed | Generic device wake eligibility only; no task ID, due time, reminder existence, text, or schedule semantics | Push payloads are generic; no plaintext reminder metadata in server logs/telemetry | Reminder content and timing can reveal personal behaviour. Server-side scheduling of semantic reminders is out of scope until a privacy-preserving design exists. |
| Recurrence rules | Rule ID, RRULE/expression, exception dates, generated occurrence state, linked object | E2EE content/local encrypted derivative | SQLCipher and encrypted sync/object storage | Opaque encrypted object metadata only | Never log expression, next occurrence, stop condition, or generated occurrence semantics | Recurrence can reveal health, work, religious, and relationship patterns. Clients evaluate it privately. |
| Attachments | Attachment ID, encrypted bytes, filename, MIME type, size, preview/thumbnail, text extraction, object key, ETag | Bytes/name/preview/extracted text/MIME semantics are E2EE content; opaque object key, encrypted byte count, storage ETag and lifecycle marker are server-visible operational metadata | Encrypted bytes in S3-compatible storage; encrypted metadata in payload; SQLCipher local cache when needed | Opaque storage key, ciphertext size, ETag/checksum of ciphertext, retention/lifecycle state | Never log filenames, previews, extracted text, semantic MIME labels, or plaintext checksums | Object storage must handle encrypted blobs without learning user content. Size leakage is accepted operational metadata. |
| Sync operations | Operation ID, encrypted payload, base/new encrypted revisions, server sequence, retry state, conflict marker, idempotency key | Encrypted operation semantics are E2EE content; opaque op ID, sequence, revision token/key version, retry count, request ID are server-visible operational metadata | Client outbox in SQLCipher; encrypted payload in PostgreSQL/S3; server sequence history in PostgreSQL | Opaque op/object IDs, ciphertext versions, server sequence, retry/error categories | Logs may include opaque request/op IDs and redacted error categories only; no decrypted operation names or object type | Server coordinates delivery and ordering but cannot interpret changes. Sequence history is persistent for current scope; detailed retention is deferred. |
| Invitations | Invite ID, inviter/invitee account IDs or email, space ID, role, encrypted key-wrapping material, expiration, acceptance state | Email/role/space membership are account/operational metadata; user-visible space name and wrapped keys are encrypted/protected | PostgreSQL for invite routing and state; encrypted payload for user-visible context/key material | Invitee email/account, inviter account, opaque space ID, role, expiration, status | Redact emails in logs where possible; never log wrapped keys or space names | Needed to invite and authorise members. Invitation cannot expose project/list names. |
| Billing/entitlements | Plan, customer/subscription IDs, invoice metadata, seat count, entitlement flags, billing email, payment processor IDs | Account/billing metadata | Billing provider and PostgreSQL entitlement table | Billing identifiers, plan, seats, entitlement state | Redact billing PII; never join with task content in logs/exports | Needed for commercial operation. Billing records are treated as persistent in current scope; detailed retention/deletion is deferred. |
| Audit events | Login/logout, device registration/revocation, member add/remove, entitlement changes, admin actions, object write envelope event | Account/operational metadata; any user-authored names or object semantics are prohibited plaintext | PostgreSQL audit tables; local security history where needed | Actor account/member ID, action category, opaque target ID, timestamp, request ID, IP/rate-limit context where needed | Log action categories and opaque IDs only. Do not log object type, names, content, or decrypted payload | Needed for security review and support. Audit metadata is treated as persistent in current scope; detailed retention/deletion is deferred. |
| Diagnostics | App version, OS version, device capability category, error code, timing counters, redacted counts, local health checks | Local-only diagnostics in Phase 1; prohibited from remote telemetry | Trusted client local storage/log viewer only; user-consented future export not designed here | None in Phase 1 | No diagnostic or telemetry data leaves the device in Phase 1. Never include decrypted content or key material | Captured delivery decision requires local-only diagnostics. Future remote support export needs explicit design and consent. |
| Backups | Local encrypted DB backup, encrypted key backup/recovery material, server PostgreSQL backup, object-store backup, restore metadata | Client data remains encrypted; backup metadata is operational; key recovery material is protected | Production backup systems only after separate backup/recovery design; current local DB backup disabled by ADR-0002 | Server backups contain ciphertext and operational metadata only | Backup logs may include job IDs/counts only; no plaintext content, filenames, keys, or search terms | Backup leaks are material threats. Detailed backup retention/deletion windows are deferred production/dogfood decisions. |

## Server-visible metadata boundary and retention expectations

Every server-visible item must be justified before an API/schema/event can use it. If a future field is not listed here, classify it before implementation.

| Metadata item | Minimal purpose | Current retention/deletion expectation |
| --- | --- | --- |
| Account ID, auth subject, email, account status | Authenticate users, route sessions, abuse prevention, support identity | Retain while account exists. Deletion/anonymisation policy is a production/dogfood decision tied to account deletion and legal needs. |
| Device ID, public device key ID, key version, last-seen, revocation status | Provision devices, wrap keys, block future access after revocation | Retain while account/space membership needs device history. Revocation prevents future key delivery but does not erase copied/decrypted data. Detailed pruning deferred. |
| Space/project opaque ID and membership role | Authorise access to encrypted objects and invitations | Retain while space/membership exists; membership removal retained in audit metadata for current scope. Detailed deletion windows deferred. |
| Opaque encrypted object ID/blob pointer/ciphertext size/ciphertext checksum/ETag | Store, retrieve, deduplicate retries, bill storage, and verify ciphertext integrity | Object bytes and storage metadata are treated as persistent for current architecture. User deletion and lifecycle/purge windows are deferred production/dogfood decisions. |
| Encryption envelope version, key ID/version, client schema/protocol version | Route compatible encrypted payloads and support migration/rollback | Retain with encrypted object or operation history while needed for sync/recovery. Detailed retention deferred. |
| Server sequence number, operation ID, idempotency key, retry/error category | Order sync, prevent duplicate writes, diagnose failed opaque operations | Server sequence history is persistent for current scope. Compaction/deletion windows are deferred. |
| Created/received/modified server timestamps | Concurrency, sync cursors, rate limits, support diagnostics | Retain with object/operation records for current scope; detailed retention deferred. Do not substitute for plaintext due/completion timestamps. |
| Storage byte counts, request counts, rate-limit counters | Billing, quotas, abuse prevention, capacity planning | Retain while operationally/accounting relevant; billing/legal retention policy deferred. |
| Billing plan, entitlement, customer/subscription/invoice IDs, seat count | Enforce paid features and business records | Billing records are persistent for current scope; statutory/business retention and account-deletion handling deferred. |
| Audit actor ID, action category, opaque target ID, request ID, IP/rate-limit context | Security investigation, admin accountability, abuse response | Audit metadata is persistent for current scope. Retention/deletion windows deferred. |
| Generic push device token/wake channel | Wake a client to evaluate encrypted local reminders or sync | Retain while device is registered; delete on device removal/account deletion where possible. Do not include plaintext reminder existence or schedule. |

Server-visible metadata must not include object type, priority, due-date presence, completion state, coarse reminder existence, user-visible names, search terms, attachment filenames, or semantic task/list/comment/reminder fields.

## Trust zones

| Zone | Trusted for plaintext? | Boundary rules |
| --- | --- | --- |
| Android app process | Yes, after user unlock/key availability | May decrypt content in memory and SQLCipher. Must avoid plaintext logs, SharedPreferences, caches, notification caches, screenshots in tests, backups, and crash reports. |
| WearOS app process | Yes, for an encrypted subset | Same plaintext rules as Android. May hold less data due to storage/battery. Must not receive plaintext via server push. |
| Android Keystore / hardware-backed secure storage | Trusted for wrapping local DB keys where available | Non-exportable keys preferred. Capability differences may be shown in local diagnostics only. Raw keys must not be logged/exported. |
| SQLCipher local DB | Trusted encrypted local persistence | May store queryable protected fields locally because the database file is encrypted. FTS/search indexes must stay inside encrypted DB without plaintext sidecars. |
| Docker-first service | Not trusted for protected content | Authenticates, authorises, stores ciphertext, assigns sequences, routes sync, handles billing and object storage. Must not decrypt payloads or infer semantic fields. |
| PostgreSQL | Not trusted for protected content | Stores account/billing/audit/operational metadata and encrypted payload references/ciphertext. A database dump must not reveal protected content. |
| S3-compatible object storage | Not trusted for protected content | Stores encrypted attachment/object bytes under opaque keys with ciphertext size/ETag. Filenames/previews/MIME semantics are encrypted. |
| Network/TLS path | Not trusted for content confidentiality beyond transport | TLS protects metadata from passive observers, but application content remains E2EE. Network observers may still see IPs, timings, sizes, and endpoints. |
| CI/GitHub Actions | Not trusted for secrets or plaintext fixtures | Use synthetic data and deterministic encrypted test vectors only. No real user content, keys, production dumps, or decrypted fixture artifacts. |
| Backups | Not trusted for protected content | Backups may contain ciphertext and operational metadata. Key recovery/restore design remains separate and must not create server decrypt capability. |
| Notifications/push provider | Not trusted for protected content | Push payloads are generic wake/sync hints only. Clients render reminder text locally from encrypted local data. |
| Diagnostics/support | Local-only in Phase 1 | No diagnostic or telemetry data leaves the device. Future support exports require explicit user consent and a separate redaction/export design. |
| Hosted operator | Untrusted for protected content | Can operate infrastructure and view allowed metadata only. Must not have content keys or plaintext task data. |
| Self-hosted operator/admin | Untrusted for protected content unless also an authorised client user | Can inspect their infrastructure metadata and ciphertext but not decrypt other users' protected content without client keys. |

## Data flows

1. Account sign-in and device registration: the client authenticates to the service, registers an opaque device ID and public key material, and stores device-private secrets in Keystore. The service stores account/device/membership metadata but no content keys.
2. Local task/list/comment/reminder work: Android/WearOS decrypts and edits content in memory, writes source of truth to SQLCipher, and updates local encrypted derivatives for search, recurrence, reminders, and ordering.
3. Encrypted sync upload: the trusted client serialises an encrypted operation/object envelope. The server receives opaque IDs, envelope/key versions, ciphertext, size, idempotency key, and request metadata, then persists it in PostgreSQL or S3-compatible object storage and assigns server sequence.
4. Encrypted sync download: the server authorises by account/device/membership metadata and returns ciphertext plus sequence/version metadata. The client decrypts locally and resolves semantic conflicts after decryption.
5. Attachments: clients encrypt attachment bytes, filenames, previews, and extracted text before upload. S3-compatible storage receives only opaque object keys, ciphertext bytes, ciphertext size, and storage integrity metadata.
6. Invitations and sharing: the server routes invitations and encrypted/wrapped key material by account/email/opaque space ID and role. User-visible space/project names remain encrypted. Revocation stops future key delivery and sync authorisation but cannot erase plaintext already decrypted or copied.
7. Notifications and reminders: clients evaluate due times, recurrence, reminder content, and notification text locally. Any push service receives only a generic wake/sync hint or device token, never task IDs, reminder existence, due times, titles, labels, or project names.
8. Billing and entitlement checks: the server stores account plan, entitlement, seat count, and billing provider IDs. Billing systems never receive task/list/comment/reminder/attachment content.
9. Diagnostics/support: Phase 1 diagnostics remain on device. A future support export may only be added after explicit design for consent, redaction, and protected-content handling.
10. CI/test fixtures: tests use synthetic content and deterministic encrypted vectors. CI may validate redaction boundaries but must not upload plaintext protected data or real secrets.
11. Backups/recovery: server backups may contain ciphertext and persistent operational metadata. Client backup/recovery and retention windows for tombstones, audit metadata, billing records, object bytes, and server sequence history are deferred production/dogfood decisions.

## Threats, mitigations, residual risks, and blockers

| Threat | Material risk | Phase 1 mitigation | Residual risk or blocker |
| --- | --- | --- | --- |
| Curious or compromised service operator | Operator reads task content, names, reminders, comments, attachments, or search terms | E2EE content encrypted before leaving trusted clients; service holds no content keys; logs/support exports prohibit plaintext; server-visible metadata is minimised and justified | Operator still sees account, membership, billing, storage size, timing, request, and sequence metadata. Accepted as operational metadata. |
| Database attacker | PostgreSQL dump reveals content or semantic metadata | PostgreSQL stores ciphertext plus opaque IDs/sequences/key versions/account metadata only; object type/priority/due/completion/reminder existence remain encrypted | Metadata leakage from sizes/timing/account relationships remains. Retention/deletion windows deferred. |
| Object-store attacker | Attachment/object dump reveals filenames/previews/bytes or object semantics | Attachment bytes, filenames, previews, extracted text, and semantic MIME fields encrypted before upload; S3 keys opaque | Ciphertext size and access timing leak. Object bytes treated persistent until lifecycle design. |
| Network attacker | Passive/active observer sees or modifies data | TLS for transport, application-level E2EE for content, authenticated encrypted envelopes in future protocol | IP addresses, endpoints, timings, payload sizes remain visible. TLS/pinning details are future implementation decisions. |
| Malicious or removed member | Member copies data before removal or continues receiving updates | Membership authorisation and key delivery stop after removal; future sync denied; key rotation/re-wrapping belongs to E2EE protocol task | Revocation cannot erase data already decrypted, copied, screenshotted, exported, or cached by a former member/device. This limitation must be user-visible. |
| Stolen device | Attacker with device accesses local protected data | SQLCipher local DB, Keystore-wrapped database key, typed key-unavailable states; app avoids plaintext local storage/backups | If attacker unlocks device/user session or compromises OS, decrypted content may be exposed. Remote wipe/recovery policy deferred. |
| Compromised client/build | Malicious client exfiltrates plaintext or keys | CI uses synthetic fixtures; release signing, dependency review, reproducible/pinned build practices are required future gates; local diagnostics only | Supply-chain/release-hardening is a Phase 1 blocker before production/dogfood release. E2EE cannot protect against a malicious authorised client. |
| Self-host admin | Admin assumes self-hosting grants plaintext access or weakens E2EE | Same E2EE boundary applies to hosted and self-hosted operators; self-host admin sees ciphertext and allowed metadata only | Admin controls infrastructure and can observe metadata/timing or deploy malicious builds if users install them. Users must trust their chosen client binaries. |
| Backup leak | Backup exposes protected data, keys, or long-lived metadata | Backups contain ciphertext and operational metadata only; local DB backups disabled until encrypted recovery/key backup design is approved | Tombstones, audit metadata, billing records, object bytes, and server sequence history are persistent for current scope; detailed deletion/retention design deferred. |
| Log/telemetry leak | Logs, analytics, crash reports, support bundles, push payloads, or CI artifacts reveal content | Protected data/key material forbidden in logs/telemetry/CI/support/push; Phase 1 diagnostics are local-only; redacted error categories only | Future remote support/export/telemetry is a blocker until consent and redaction design exists. |
| Supply-chain compromise | Dependency/action/container/build system leaks secrets or plaintext | Pin and review dependencies/actions as CI matures; no plaintext real fixtures; minimal GitHub token permissions; no protected secrets for untrusted forks | Full SLSA/signing/provenance and dependency scanning policy remains a Phase 1 release blocker, not solved by this spec. |
| Malicious API client or replay | Client submits malformed encrypted operations, stale revisions, or abusive traffic | Server validates auth, membership, opaque IDs, envelope versions, size limits, idempotency, rate limits, and sequence ordering without decrypting content | Semantic conflict validation occurs on trusted clients after decryption. Exact protocol fixtures and conflict rules are future blockers. |
| Lost recovery material | User loses all authorised devices/keys | Account authentication remains separate from content-key recovery; service cannot reset content keys | Encrypted data may be permanently unrecoverable. Backup/recovery design is required before enabling recovery claims. |

## Boundaries for future work

### Notifications

Notification text, task IDs, project/list/label names, due times, recurrence details, reminder existence, and completion state stay on trusted clients. Server push may only carry generic wake/sync hints to a device token. Clients render notifications from SQLCipher local data.

### Attachments

Clients encrypt bytes and all human-readable metadata before upload. Object storage keys are opaque and must not encode account email, project/list/task names, filenames, object type, MIME semantics, or preview state. Server may record ciphertext size, ETag, storage class, and lifecycle state.

### Backup and recovery

Production backup/recovery is not approved by this specification. Server backups may contain ciphertext and allowed operational metadata. Local Android/WearOS backup remains disabled or excludes SQLCipher DB, WAL, sidecars, schema state, and key-wrapper storage until encrypted backup/recovery is accepted. Losing all authorised devices/recovery material may make content unrecoverable.

### Diagnostics and support export

Phase 1 diagnostics are local-only. No diagnostic or telemetry data leaves the device. Local diagnostics may show app version, OS version, capability categories, redacted counts, and typed error codes, but never decrypted content, filenames, search terms, reminder details, or key material. Future support export requires explicit user consent and a separate redaction/export design.

### CI and test fixtures

CI, Docker logs, GitHub Actions, artifacts, issue reports, and PR bodies are untrusted for protected content. Tests must use synthetic non-user content and deterministic encrypted vectors. Future checks should scan server logs, push payload builders, analytics events, object metadata, and unencrypted local storage for prohibited field names and fixture plaintext.

### Offline, sync, search, and reminders

Clients own semantic work after decryption: search, recurrence evaluation, reminder scheduling, conflict resolution, move validation, completion state, and order-key interpretation. The server may order opaque encrypted operations and enforce authorisation/rate/size constraints, but not inspect semantic task data.

### Revocation

Member/device revocation prevents future access: the server must stop authorising sync and key delivery for removed members/devices, and future E2EE protocol work must define key rotation/re-wrapping where needed. Revocation cannot erase data already decrypted, copied, exported, photographed, cached, or otherwise retained by a previously authorised client or human.

## Security and privacy test cases derivable from this model

| Rule | Example test case |
| --- | --- |
| E2EE content does not leave trusted clients as plaintext | Build server API tests with sample task title, list name, label, due date, reminder text, recurrence rule, and attachment filename; assert only ciphertext/opaque IDs cross the server boundary. |
| Object type and delivery fields remain encrypted | Add contract tests that reject plaintext object type, priority, due-date presence, completion state, and reminder-existence fields in server requests, responses, events, indexes, logs, and push payloads. |
| Logs redact protected content and keys | Exercise server/client error paths with synthetic protected strings and assert logs/crash breadcrumbs/artifacts contain request IDs and typed error categories only. |
| Push payloads are generic | Unit-test notification builders so remote payloads carry only a device wake/sync hint, not task IDs, titles, due timestamps, project/list/label names, reminder text, or recurrence data. |
| Local diagnostics stay local | Test diagnostics exporters are disabled for remote transmission in Phase 1 and that local reports redact content/key fields. |
| Attachments are opaque to storage | Upload fixture attachment bytes/filename/preview through future client code and assert S3 object key/metadata contain no plaintext filename, MIME semantic label, preview text, or content hash of plaintext. |
| PostgreSQL backup does not reveal content | Dump a test database after inserting synthetic encrypted objects and scan for protected fixture strings and field names. |
| SQLCipher local storage contains private data only encrypted at rest | Reuse ADR-0002 tests to scan database/WAL files for sample plaintext, while allowing local queryability only after decrypting through the trusted client. |
| Revocation prevents future access but not past copies | Integration tests should remove a member/device and assert future sync/key delivery fails; documentation/UI tests assert previously decrypted data cannot be clawed back. |
| CI fixtures are safe | CI policy tests reject real-looking secrets, production dumps, decrypted task fixture corpora, and protected field names in artifacts. |
| Sync ordering without semantic visibility | Future protocol tests should submit opaque encrypted operations and assert the server assigns sequences/idempotency without requiring plaintext operation type or object type. |

## Non-goals and explicit blockers

This specification does not choose final cryptographic algorithms, key hierarchy, encrypted envelope schema, key rotation cadence, recovery UX, sync conflict algorithm, production deletion windows, telemetry export design, release signing/provenance policy, or business/legal retention rules. Those remain follow-up architecture/security tasks.

Phase 1 blockers before any dogfood/production use with real synced task data:

- accepted E2EE protocol and encrypted entity envelope;
- device provisioning, key backup/recovery, sharing, revocation, and key rotation design;
- encrypted sync protocol and conflict model;
- backup/recovery design with deletion/retention windows;
- support export/remote telemetry decision if anything is allowed to leave device later;
- release/build supply-chain controls sufficient for trusted client binaries.
