# Phase 1 foundation dependency map

Status: working map for Notion task `3b0319db-76c8-8101-9b53-ccb70a146fc0`
Source of truth: Notion brief, project page, Main Tasks DB, ADR-0001, and repo ADRs
Last reviewed from Notion: 2026-08-29

## Purpose

Phase 1 must establish the technical foundation before product features start depending on unstable security, storage, sync, API, or release assumptions. This map records the safe implementation order and the readiness gates that future agents and contributors must check before moving child tasks to `Ready` or writing code.

ADR-0001 is already accepted: end-to-end encryption is mandatory before any usable release stores or syncs real task data. ADR-0003 is accepted for the Phase 1 privacy threat model and metadata boundary. ADR-0004 is accepted for the E2EE protocol and encrypted entity envelope. ADR-0005 is accepted for encrypted offline sync and conflict resolution. ADR-0006 is accepted for device identity, provisioning, secure key storage, and seamless local-first bootstrap: first launch must allow immediate encrypted local task/reminder capture without mandatory account creation, sign-in, server registration, pairing, recovery setup, or crypto/security wizard.

## Backlog state at review time

| State | Phase 1 items |
| --- | --- |
| In progress | Parent foundation task; GitHub repository workflow; Docker-first stack; encrypted SQLite persistence architecture |
| Ready | Groups of lists / Projects; drag-and-drop reordering; repeating notification pings |
| Todo | Remaining foundation architecture/setup/CI/release tasks and Phase 1 product features |
| Idea | Due dates/times/duration/deadlines/time zones |

Several feature tasks are marked `Ready` in Notion, but implementation is still blocked by unresolved Phase 1 foundations and by the absence of application/client/server scaffolding in `main`:

- Groups of lists / Projects depends on repository/source-control foundation, Docker-first PostgreSQL/server stack, versioned migrations, encrypted entity envelope, device provisioning, encrypted local persistence, offline sync architecture, and the domain hierarchy/key-scope decision path.
- Drag-and-drop reordering depends on the metadata boundary, order-key algorithm, hierarchy model, local persistence, sync/conflict policy, and API move contract.
- Repeating notification pings depend on task due/reminder modelling, Android notification foundation, encrypted local persistence, encrypted sync, and privacy metadata boundary; current decisions keep repeat/stop state local per device and avoid server-side repeating wake-up scheduling.

Do not implement these features until their prerequisite foundation tasks are accepted or the feature is split into a deliberately narrower, dependency-free slice with explicit acceptance criteria and tests.

## Dependency layers

### 0. Governance and contribution boundary

Required before other work becomes durable:

- GitHub repository and protected contribution workflow.
- Canonical ADR location in `docs/adr/`.
- Notion delivery workflow and task state policy.
- PR branch/check/review expectations.

This layer is the base for all later changes because it controls where accepted decisions live and what evidence is required before work is Done.

### 1. Privacy threat model and metadata boundary

Required before storing or syncing real task data:

- Define the privacy threat model and server-visible metadata boundary.
- Decide which fields can be visible to the server and which are encrypted client content.
- Explicitly cover task hierarchy and ordering metadata before implementing drag-and-drop, lists, reminders, recurrence, or search.
- Record unresolved privacy/security choices as ADRs or task blockers.

Blocked downstream work includes task/list APIs, encrypted object storage, sync, search, reminders, sharing, and drag-and-drop hierarchy/order moves.

### 2. Cryptographic model and device/key lifecycle

Required before usable client persistence or sync:

- Specify the E2EE protocol and encrypted entity envelope. ADR-0004 and `docs/e2ee-protocol-v1.md` define the accepted v1 protocol, envelope and deterministic vectors.
- Design device identity, provisioning, secure key storage, recovery, key backup, sharing, membership changes, revocation, and key rotation. ADR-0006 and `docs/device-identity-provisioning.md` define the accepted device identity/provisioning boundary, including invisible encrypted local bootstrap, deferred account/server registration until network-dependent capabilities, just-in-time notification permission for reminders, and explicit user-mediated trust for new devices/watch/key/recovery/security-policy flows.
- Produce versioned protocol fixtures that future Android, WearOS, server, and other clients can reuse.

Downstream code must not invent encryption envelope fields, key identifiers, recovery semantics, device provisioning semantics, or sharing behaviour without these decisions. Device provisioning implementation, recovery, encrypted key backup, sharing, revocation implementation and production implementation remain explicit deferred gates.

### 3. Client storage and offline model

Required before Android/WearOS features persist real task data:

- Define encrypted SQLite persistence for Android and WearOS using Room, SQLCipher, and Android Keystore protected key material.
- Specify local schema boundaries for encrypted content, visible metadata, sync queue, tombstones, revisions, and conflict state.
- Keep search, recurrence, reminders, and notification evaluation private on trusted clients.

This layer must be compatible with arbitrary-depth task hierarchy and stable sibling ordering keys before drag-and-drop implementation begins.

### 4. Server foundation and persistence

Required before server APIs become feature-bearing:

- Scaffold the Go modular-monolith server with explicit module boundaries.
- Establish Chi HTTP transport, middleware, privacy-safe errors, and observability.
- Establish versioned PostgreSQL migrations and checksum discipline.
- Implement PostgreSQL access with pgx/sqlc.
- Design the PostgreSQL server schema and encrypted object storage.
- Implement a PostgreSQL-backed background job runner if async Phase 1 work requires it.

Applied migrations must never be edited after live use; add forward migrations and upgrade tests instead.

### 5. API, sync, and compatibility contracts

Required before feature APIs and multi-device behaviours:

- Define versioned OpenAPI contracts and compatibility policy.
- Specify encrypted offline sync and deterministic conflict resolution.
- Document event contracts for move, create, update, delete, completion, reminder, sharing, and attachment workflows as they become in scope.
- Define validation errors without relying on decrypted task content.

For drag-and-drop specifically, the move contract must cover task id, old/new parent where applicable, order key, revision/vector metadata, stale revision handling, cycle rejection, orphan prevention, and cross-list/project boundaries.

### 6. CI, release, and self-hosting evidence

Required before claiming foundation Done:

- Docker-first development/test/self-hosted stack.
- GitHub Actions validation and security gates: formatting, linting, compilation, tests, migration checks, dependency checks, and secret scanning.
- Versioned server image publication and rollback path.
- Signed Android and WearOS release artifacts once native modules exist.
- Self-hosting/licensing/contributor documentation.

Android and WearOS must remain easy to clone, build, and install from Paul's laptop via Gradle and ADB, not hidden behind container-only workflows.

## Feature readiness gates

Before moving a Phase 1 product feature to implementation, verify:

1. The feature page has accepted acceptance criteria and a test plan.
2. Any E2EE, metadata, local storage, sync, or API dependency is either already accepted or explicitly declared out of scope for the first slice.
3. The feature can be tested without real plaintext production data leaving a trusted client.
4. Android/WearOS build, install, or display expectations are documented when affected.
5. The work can be delivered in one focused PR, or split into child implementation tasks.

## Drag-and-drop implementation blockers

The current drag-and-drop feature should be treated as blocked until these decisions are resolved:

- Whether `parent_task_id` and `order_key` are server-visible metadata or encrypted fields.
- The canonical order-key algorithm all clients must use.
- Cycle/orphan validation responsibility across client, server, and sync replay.
- Cross-list/project move policy for Phase 1.
- Local encrypted persistence schema for hierarchy/order data.
- Offline sync conflict semantics for concurrent moves, move/delete conflicts, and stale revisions.
- Android accessibility fallback behaviour and WearOS display-only boundary.

A safe first implementation slice after those decisions is: domain/order-key tests, tree reconstruction tests, and API validation tests before Android drag UI.

## Done evidence for the parent foundation task

The parent foundation task is not Done until:

- Every child architecture/setup/CI/release task is either specified, blocked with a named decision, Ready-approved, or delivered.
- The Phase 1 dependency order is reflected in Notion and the repository.
- Required ADRs are accepted and linked.
- CI proves formatting, linting, compilation, tests, migration checks, and security/dependency checks.
- Docker server stack and native Android/WearOS build/install commands are documented and verified when modules exist.
- Accepted criteria, automated tests, documentation, and delivery links are recorded back to Notion.
