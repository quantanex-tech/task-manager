# Encrypted client persistence specification

Related ADR: [`ADR-0002`](../adr/0002-encrypted-client-sqlite-persistence.md)  
Related Notion task: `3b0319db76c881968a6bd0c84ae84667`

This document specifies the Phase 1 Android/WearOS local persistence layer. It is intentionally narrower than sync, sharing, reminders scheduling, importers or encrypted backup/recovery. It establishes the local source of truth those later features must respect.

## Goals

- Provide offline create/read/update/delete/reorder storage for task data on Android and WearOS.
- Encrypt all private task data at rest with SQLCipher-backed SQLite.
- Protect the database key with Android Keystore.
- Keep UI and domain logic independent of Room, SQLCipher and platform storage APIs.
- Preserve migration, outbox and tombstone shapes needed for future E2EE sync.
- Define a test plan that can run without live services or private credentials.

## Non-goals

- No server sync or conflict resolution implementation.
- No Todoist importer, reminders scheduler, widgets, attachments or natural-language parser.
- No production local database backup or key recovery flow.
- No plaintext server-readable task metadata beyond future metadata explicitly approved by the E2EE threat model.

## Storage boundary

### Allowed outside encrypted SQLite

Only non-content operational metadata may live outside the encrypted database:

- Keystore alias and wrapped database-key envelope metadata.
- Boolean setup flags that do not reveal user content.
- Notification channel IDs and opaque local notification IDs when required by Android APIs.
- Crash/error categories such as `KEY_UNAVAILABLE`, `MIGRATION_FAILED` or `DATABASE_CORRUPT`.

### Forbidden outside encrypted SQLite

Do not write the following to SharedPreferences, logs, app-private plaintext files, caches, analytics, crash breadcrumbs, backups or test artifacts:

- task titles, descriptions, comments or markdown bodies;
- group/list/project names;
- due dates, reminder text, recurrence rules and completion history;
- search terms derived from tasks;
- database keys, wrapped-key plaintext, SQLCipher passphrases or decrypted sync keys.

Until encrypted recovery is designed, Android backup should be disabled or explicitly exclude the database, WAL files, schema state, SQLCipher sidecars and key-wrapper storage.

## Architecture

```text
Android/WearOS UI
      |
      v
Domain use cases and models
      |
      v
LocalTaskStore / TaskRepository interface
      |
      v
Room DAOs + mappers + transaction coordinator
      |
      v
SQLCipher encrypted SQLite database
      |
      v
Android Keystore-protected database key
```

### Repository interface

The first local-store boundary should expose suspend functions and observable streams, with Room hidden behind the adapter:

```kotlin
interface TaskRepository {
    fun observeTask(taskId: TaskId): Flow<Task?>
    fun observeList(listId: ListId): Flow<List<Task>>
    suspend fun createTask(command: CreateTask): TaskId
    suspend fun updateTask(command: UpdateTask)
    suspend fun completeTask(taskId: TaskId, completedAt: Instant)
    suspend fun deleteTask(taskId: TaskId, deletedAt: Instant)
    suspend fun reorderTasks(listId: ListId, orderedTaskIds: List<TaskId>, changedAt: Instant)
}
```

Implementation notes:

- Domain models use stable IDs, instants and value objects; they do not reference `@Entity`, `@Dao`, `SupportSQLiteDatabase`, SQLCipher factories or Android `Context`.
- Mappers are the only layer that knows how database rows map to domain objects.
- Future sync workers should use the same repository/transaction coordinator rather than bypassing domain invariants.

## First-slice schema

The schema is intentionally explicit about future sync without implementing it. Column names are illustrative; exact names can change during implementation if the same semantics are preserved.

### `lists`

Stores local task groups/projects/lists.

| Column | Purpose |
| --- | --- |
| `id` | Stable offline-generated ID, e.g. UUID/ULID. |
| `name` | Private list name inside encrypted DB. |
| `sort_key` | Local ordering key. |
| `created_at`, `updated_at` | Client timestamps. |
| `deleted_at` | Nullable soft-delete marker when retained for tombstone generation. |
| `version` | Local monotonic version for idempotent mutation records. |

### `tasks`

Stores task source-of-truth fields.

| Column | Purpose |
| --- | --- |
| `id` | Stable offline-generated task ID. |
| `list_id` | Parent list/group/project ID. |
| `title` | Private task title inside encrypted DB. |
| `description_markdown` | Private markdown body, nullable. |
| `status` | `open`, `completed`, `deleted` or future-compatible enum. |
| `sort_key` | Reordering value stable across offline writes. |
| `due_at`, `timezone` | Nullable due/reminder base fields. |
| `recurrence_rule` | Nullable client-evaluated recurrence metadata. |
| `created_at`, `updated_at`, `completed_at`, `deleted_at` | Client timestamps. |
| `version` | Local monotonic version for idempotency and future sync. |

### `task_mutation_outbox`

Records local changes for future encrypted sync replay.

| Column | Purpose |
| --- | --- |
| `id` | Stable mutation ID. |
| `entity_type`, `entity_id` | Target entity. |
| `operation` | Create/update/complete/delete/reorder. |
| `base_version`, `new_version` | Idempotency and conflict inputs. |
| `created_at` | Local mutation timestamp. |
| `payload_digest` | Digest of canonical local payload if needed for idempotency; not a server plaintext payload. |
| `sync_state` | Pending/applied/failed for future worker. |

### `tombstones`

Retains deletion facts needed by future sync and multi-device reconciliation.

| Column | Purpose |
| --- | --- |
| `entity_type`, `entity_id` | Deleted object. |
| `deleted_at` | Local deletion timestamp. |
| `deleted_version` | Entity version at deletion. |
| `retention_until` | Date after which future policy may purge. |

### `sync_cursors`

Placeholder for future encrypted sync checkpoints. It should exist only when a sync task needs it; if created early, it remains opaque and contains no task plaintext.

| Column | Purpose |
| --- | --- |
| `scope` | Account/device/list scope. |
| `cursor_ciphertext` or `opaque_cursor` | Future server cursor. |
| `updated_at` | Last checkpoint update. |

### `key_metadata`

Non-secret metadata about the local DB key state. Raw keys never enter this table.

| Column | Purpose |
| --- | --- |
| `key_id` | Current logical database key ID. |
| `keystore_alias` | Alias used for unwrapping. |
| `created_at`, `rotated_at` | Audit metadata. |
| `hardware_backed` | Capability flag where available. |

## Key management

1. On first launch, generate a random 256-bit SQLCipher database key.
2. Generate or reuse an Android Keystore key whose alias is app-scoped and environment-specific.
3. Wrap the database key with Keystore-backed AES-GCM or another approved authenticated encryption mode.
4. Store only the wrapped key blob, IV and non-sensitive metadata in app-private no-backup storage.
5. Open Room with a SQLCipher support factory built from the unwrapped key; wipe key byte arrays as soon as the platform/library allows.
6. On key-unavailable, authentication-required, corrupted-wrapper or unsupported-cipher errors, return a typed persistence error and do not create a new empty database over existing data.

### Rotation

Rotation is not required for the first local slice, but the design must leave a safe path:

- unwrap current DB key;
- generate and wrap a new DB key;
- run SQLCipher `rekey` in a controlled migration/maintenance operation;
- commit new key metadata only after `rekey` succeeds;
- preserve old wrapped key until the database can be reopened with the new key, then delete it.

## Transactions and crash consistency

Use a single database transaction for operations where domain state and sync metadata must move together:

- create/update/complete/delete a task and insert its outbox mutation;
- delete an entity and insert/update its tombstone;
- reorder a list and write all affected ordering mutations;
- apply a future sync batch and advance its cursor.

A crash must not leave a completed task without its mutation record or a sync cursor advanced beyond unapplied data.

## Migrations

- Export Room schema JSON for every release that can reach users.
- Add forward migrations for every schema change; do not edit migrations that may already have been applied to a live database.
- Test fresh creation and all supported version-to-version upgrades.
- Keep synthetic migration fixtures small and free of real user content.
- Prefer additive schema evolution; destructive migration is allowed only for disposable prototypes clearly excluded from usable releases.
- Document user-visible recovery for unrecoverable corruption or unsupported old versions.

## WearOS subset

WearOS uses the same repository semantics where possible, but it may retain a smaller local subset:

- tasks assigned to watch surfaces, quick actions or imminent reminders;
- opaque IDs and versions needed to reconcile with Android/client sync later;
- no independent plaintext key or backup path.

Before implementing WearOS storage, run a compatibility spike or instrumented test proving SQLCipher open, CRUD and migration paths on the minimum supported WearOS target.

## Indexing

Indexes are local-only and live inside SQLCipher:

- `tasks(list_id, status, sort_key)` for list rendering;
- `tasks(due_at)` for upcoming/reminder queries;
- `tasks(updated_at)` and `task_mutation_outbox(sync_state, created_at)` for future sync workers;
- optional SQLite FTS only if it remains within the encrypted database and does not create plaintext sidecars.

## Error model

Expose typed errors rather than raw exceptions:

| Error | User/developer behaviour |
| --- | --- |
| `KeyUnavailable` | Prompt for required device unlock or report that secure key material is unavailable. |
| `DatabaseCorrupt` | Stop writes, offer reset/export/recovery only when safe, and avoid logging content. |
| `MigrationFailed` | Block startup for affected data, include schema versions and safe diagnostics only. |
| `UnsupportedCipher` | Report unsupported device/runtime combination. |
| `StorageFull` | Surface actionable storage guidance and retry behaviour. |

## Test plan

### Unit tests

- Domain-to-entity mappers preserve IDs, timestamps, nullable fields, tombstones and versions.
- Repository validation rejects invalid reorder lists, unknown parent lists and stale commands where applicable.
- Transaction coordinator writes entity rows and outbox/tombstone rows atomically.
- Error mappers redact task content and key material.

### Robolectric or instrumented tests

- Fresh encrypted database creation/open with a synthetic Keystore/key provider.
- Offline CRUD and reorder through `TaskRepository` with network unavailable.
- App restart/reopen preserves tasks.
- Missing key/corrupted wrapper returns `KeyUnavailable` or equivalent typed error without overwriting the database.
- SQLCipher database files do not expose sample task titles through simple file/string scans in test artifacts.

### Migration tests

- Fresh install creates the latest schema.
- Every supported historical schema fixture migrates to latest and repository smoke tests pass.
- Migration tests run in GitHub Actions without live services or private credentials.

## Acceptance mapping

| Notion acceptance criterion | Design response |
| --- | --- |
| Opening the database requires key material protected by Keystore. | ADR-0002 and this spec require a Keystore-wrapped random DB key before SQLCipher open. |
| No task content is written to unencrypted preferences, logs, caches or backups. | Storage boundary forbids content outside encrypted SQLite and disables backup until recovery is designed. |
| Schema migrations preserve encrypted data and are tested from every supported version. | Migration section requires Room schema exports, forward migrations and version fixtures. |
| Outbox and cursor updates are crash-safe. | Transaction section requires entity/outbox/tombstone/cursor atomicity. |
| The domain layer has no Room- or SQLCipher-specific dependency. | Repository boundary keeps Room/SQLCipher in the adapter. |
| Loss and corruption behaviours are user-visible and recoverable where cryptographically possible. | Error model defines typed key/corruption/migration/storage failures and safe recovery constraints. |
