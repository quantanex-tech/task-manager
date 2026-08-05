# ADR-0002 — Android and WearOS use encrypted SQLite behind a replaceable repository boundary

Status: Proposed  
Date: 2026-08-05  
Decision owner: Paul  
Related task: Notion page `3b0319db76c881968a6bd0c84ae84667`

## Context

Android and WearOS are the first client targets, and they must work offline before reminders, sync, widgets, importers or wearable interactions can be trusted. ADR-0001 makes end-to-end encryption mandatory for the first usable release, so local storage cannot introduce plaintext shortcuts that later conflict with the E2EE data model.

The Phase 1 persistence layer needs to:

- store task content reliably on-device while the network is unavailable;
- keep task titles, descriptions, dates, reminder details, completion history, ordering and group names out of plaintext preferences, logs, caches and backups;
- support tested forward migrations from every supported schema version;
- leave room for encrypted sync, outbox replay, tombstones and future conflict resolution;
- allow Android and WearOS UI code to use a stable domain repository instead of Room, SQLCipher or platform-specific storage APIs directly.

## Decision

Use SQLite on each Android and WearOS client, accessed through Room 2.8.x and encrypted at rest with SQLCipher Community Edition.

The database key is a random, app-generated secret protected by Android Keystore. The Keystore key is non-exportable where hardware-backed secure storage is available; devices that only provide software-backed Keystore may still run, but that capability difference must be visible in diagnostics and security review. The wrapped database-key blob and non-sensitive key metadata may be stored in app-private no-backup storage. Task content must not be stored there.

The domain layer depends only on repository interfaces and domain models. Room entities, SQLCipher open-helper code, SQL migration details and key-management implementation remain in the Android/WearOS persistence adapter.

Within the encrypted SQLite database, task fields may be stored in queryable form for client-side search, sorting, reminders and recurrence evaluation. This does not weaken ADR-0001 because the database file is encrypted at rest and the server never receives plaintext. Future sync payloads must still be encrypted before leaving trusted clients.

## Consequences

### Positive

- Android and WearOS get a mature local database with Room compile-time query validation and migration tooling.
- SQLCipher protects the full database file, WAL and page content at rest rather than relying on scattered field-level encryption.
- A repository boundary keeps UI/domain code portable and leaves the storage adapter replaceable if SQLCipher, Room or platform constraints change.
- Local CRUD, reordering, tombstones and outbox records can be committed atomically in one database transaction.
- Client-side search, recurrence and reminders remain possible without server-readable plaintext.

### Negative / costs

- SQLCipher integration and migration tests are more complex than plain Room.
- Keystore availability, lock-screen state, biometric enrollment changes and device restore behaviour require explicit error handling.
- WearOS storage and battery constraints may require a smaller retained subset of data than Android.
- A future E2EE envelope may require additional encrypted columns or tables; schema choices must avoid assuming server-readable content.

### Non-goals

- This ADR does not define the full E2EE protocol, encrypted sync conflict resolution, key recovery, encrypted backups, sharing, importers or notification scheduling.
- This ADR does not permit plaintext server storage or server-managed content keys.
- This ADR does not introduce production local database backups. Backups are disabled until a separate recovery/key-backup design is approved.

## Required implementation rules

1. Open the database only after the database key has been unwrapped through Keystore or a typed key-unavailable error has been returned.
2. Set Android backup policy to exclude the database, WAL files and key-wrapper storage until encrypted backup/recovery is designed.
3. Keep private task content inside the encrypted database only. Do not write task titles, descriptions, reminder text or due dates to SharedPreferences, logs, crash breadcrumbs, notifications caches, files, analytics or test artifacts.
4. Use a `TaskRepository`/local-store interface for UI and domain code. Room DAOs are implementation details.
5. Put domain mutations, outbox insertion, tombstone creation and cursor/checkpoint updates in one SQL transaction when they must be crash-consistent.
6. Export Room schema JSON and add forward-only migrations. Do not edit an already-applied migration if checksum or migration-history tooling could invalidate live installs.
7. Test fresh creation and every supported upgrade path with synthetic data that is safe to commit.
8. Surface key loss, unsupported cipher, corruption and migration failure as typed user-visible recovery states; never log keys or task content.

## Open follow-ups

- Confirm SQLCipher compatibility on the minimum supported WearOS versions before the first wearable implementation depends on it.
- Decide the E2EE content envelope and sync conflict model before server sync or multi-device sharing moves to Ready.
- Design encrypted backup/recovery separately before enabling any automatic local database restore path.
