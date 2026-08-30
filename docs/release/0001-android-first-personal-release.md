# Android-first personal dogfood/alpha release target

Status: Candidate pending Paul's exact-document acceptance
Source task: Notion page `3cc319db-76c8-8138-abea-d0410f17b4ff`
Source decision: Paul replied `DEFINE RELEASE TARGET` after continuation task `t_a6656ee8`
Repository base for this candidate: `origin/main` at `6f99dc2bec07286e2b6666c7b4886c765f9eec36`

## Purpose

This document defines the smallest Android-phone-first personal release that can be treated as a usable dogfood/alpha candidate for Paul's own task and reminder capture. It is a release target and project-level Definition of Done, not an implementation, deployment, Notion status change, production launch, or statement that Paul has accepted every detailed criterion below.

The target preserves the accepted architecture in:

- [ADR-0001 — E2EE is mandatory for the first usable release](../adr/0001-phase-1-e2ee-mandatory.md)
- [ADR-0002 — Android and WearOS use encrypted SQLite behind a replaceable repository boundary](../adr/0002-encrypted-client-sqlite-persistence.md)
- [ADR-0003 — Phase 1 privacy threat model and metadata boundary](../adr/0003-phase-1-privacy-threat-model-and-metadata-boundary.md)
- [ADR-0004 — Phase 1 E2EE protocol and encrypted entity envelope](../adr/0004-phase-1-e2ee-protocol-and-encrypted-entity-envelope.md)
- [ADR-0005 — Phase 1 encrypted offline sync and conflict resolution](../adr/0005-phase-1-encrypted-offline-sync-and-conflict-resolution.md)
- [ADR-0006 — Phase 1 device identity, provisioning, and secure key storage](../adr/0006-phase-1-device-identity-provisioning-and-secure-key-storage.md)
- [ADR-0007 — Private search, recurrence, reminders, and notifications](../adr/0007-private-search-recurrence-reminders-and-notifications.md)

## Candidate release label

Working label: Android-first personal dogfood/alpha.

The candidate is Android phone first. WearOS, server sync, self-hosting, web, desktop, iOS, teams, and public production deployment are not required for this first usable release.

## Release-state language

Use these states separately in handoffs, PRs, Notion updates, and release notes:

| State | Meaning for this release target |
| --- | --- |
| Implemented | Code and docs for a scoped slice exist in the repository. |
| Technically approved | An independent technical reviewer has approved the evidence for a slice or document. |
| Human accepted | Paul has explicitly accepted the exact document, artifact, or behaviour under review. This candidate is not human accepted until Paul does that separately. |
| Integrated | The accepted change is merged into the intended branch through the project workflow. |
| Deployable | A deterministic build produces an installable Android dogfood artifact with documented install steps and required verification evidence. |
| Live / usable | Paul has installed and used the artifact on a real Android device for the intended personal dogfood workflow and has separately accepted that use. |

Technical checks can establish implemented, technically approved, integrated, and deployable states. They must not claim Paul's human acceptance or live/usable state.

## Minimum product scope

The first usable release must let Paul open the Android phone app and quickly start adding local tasks and reminders without mandatory account creation, sign-in, server registration, device pairing, recovery setup, or a crypto/security wizard.

The minimum included behaviour is:

1. One personal Inbox/list.
2. Create a task with at least a title.
3. View the Inbox/list and task detail or edit surface.
4. Edit an existing task.
5. Complete a task.
6. Undo completion.
7. Delete a task.
8. Add, edit, remove, and view one optional exact date/time reminder on a task.
9. Receive a local privacy-preserving Android notification for that reminder when the device, OS capability, key state, and notification permission allow it.
10. See explicit degraded states when exact reminders or notifications cannot be delivered as requested.
11. Use the app in light and dark presentation.
12. Continue using the included local workflow while offline.

## Measurable quick-start requirement

The normal first-launch path satisfies "quickly start adding tasks and reminders" only if all of these are true in instrumentation or real-device evidence:

- From a fresh install on a supported Android phone, the user can create a first local encrypted task from the first interactive app screen without creating an account, signing in, registering a device with a server, pairing another device, setting up recovery, or completing a crypto/security wizard.
- The first task entry flow requires no more than one intentional navigation action before text entry is possible.
- A task with a title can be durably saved in encrypted local storage while the device is offline.
- Adding an exact date/time reminder to that task is available in the local flow without requiring network access.
- Notification permission is requested only when needed for reminder delivery. Denial does not block saving the task or reminder; it records a clear local disabled-notification state and shows how to enable notifications later.
- Setup failures never fall back to plaintext persistence. If encrypted storage or key material is unavailable, user-entered drafts may remain only in volatile UI state until encrypted storage is available, or the app must show a typed recovery/error state.

## Privacy, encryption, and network boundaries

This release is local-first and offline-capable. It must not store real task data unless the accepted encrypted local persistence, key-handling, and privacy requirements from ADR-0001 through ADR-0007 are implemented and verified.

Binding rules:

- No real task data may be persisted in plaintext files, SharedPreferences, logs, crash breadcrumbs, analytics, CI artifacts, Android backup, notification caches, or other side channels.
- Local durable storage for real task and reminder data must be encrypted according to the accepted Android/WearOS encrypted SQLite and key-handling boundaries.
- The normal first-run key and local-store bootstrap must be effectively invisible to the user in the success path.
- No task data, reminder details, task identifiers, due timestamps, notification text, search terms, object semantics, content keys, database keys, pairing secrets, or protected diagnostics may sync or otherwise leave the trusted Android device in this release.
- Server APIs, account creation, sync, and content-free push wake-ups are out of scope for the release candidate. If any network stack is present for unrelated technical reasons, automated evidence must show it does not transmit task data or protected reminder data.
- Android notifications must render meaningful content only locally on a trusted keyed device, respect platform privacy controls, and fall back to generic/private presentation when content is suppressed, redacted, unavailable, or undecryptable.
- Exact reminders must fail visibly to a degraded state when exact-alarm capability, permissions, OS policy, key state, or scheduler recovery is insufficient. Silent best-effort downgrade is not acceptable.

## Explicit first-release non-goals

The following are future work for the roadmap and are not rejected product directions. They are intentionally outside this first release:

- account creation, login, account recovery, and cloud sync;
- server feature APIs, server-side task storage, server-side reminder scheduling, and content-free push infrastructure;
- multi-device use and standalone device provisioning;
- WearOS implementation;
- projects, groups of lists, labels, sections, or arbitrary hierarchy beyond the single Inbox/list;
- drag-and-drop ordering and custom sort ordering;
- recurrence, repeating pings, snooze rules, saved reminder templates, or calendar integration;
- full-text search, filters, and saved views;
- sharing, teams, billing, organisation administration, and commercial workflows;
- attachments, comments, imports, exports, widgets, public APIs, or integrations;
- iOS, web, desktop, browser extension, and non-Android clients;
- public production deployment, hosted service availability, and self-hosted feature-bearing server;
- production release hardening beyond what is necessary to honestly install and dogfood the local Android artifact.

## Project-level Definition of Done

This release target is done only when the following evidence exists and is independently reviewable.

### Source, policy, and release governance

- The release scope, non-goals, known limitations, and state language are documented and linked from the repository.
- Accepted ADR-0001 through ADR-0007 remain uncontradicted and linked from affected specs or release notes.
- Repository policy and CI checks are green for the release branch.
- The release artifact is built from a known commit, with provenance recorded in the release notes or handoff.
- No Notion task is marked `Done` or `Live` solely because automated checks passed; Paul's separate real-device human acceptance remains required.

### Android build and artifact

- A deterministic Android build command is documented and passes from a clean checkout.
- The output is either signed with an approved dogfood/internal signing setup or explicitly labelled as a non-production dogfood/debug artifact.
- The artifact can be installed with documented ADB instructions compatible with [the Android/WearOS install guide](../android-adb-install.md).
- Version name/code, application id, signing posture, supported Android range, and debug/dogfood limitations are recorded.

### Core workflow proof

- Automated domain tests cover task create, view/list, edit, complete, undo completion, delete, and optional exact reminder create/edit/remove behaviour.
- Automated storage tests prove task and reminder data are durably persisted only after encrypted local storage and key material are available.
- Android instrumentation or UI tests prove the first-launch quick-start workflow, Inbox/list workflow, edit workflow, completion/undo workflow, delete workflow, reminder entry, permission-denied state, and light/dark presentation.
- Offline tests prove the included task and reminder workflows work with network disabled.

### Reminder and notification proof

- Exact-alarm success tests cover scheduling and local notification display for a selected date/time when OS capability and permission allow it.
- Degraded-state tests cover notification permission denial, exact-alarm capability unavailable/revoked, device reboot, app update or process restart, time-zone change, daylight-saving transition where relevant, missing key material, and corrupted/unavailable encrypted local state.
- Reboot and time-zone recovery expectations are documented and tested: reminders are rescheduled or explicitly marked degraded without guessing, leaking details, or silently downgrading exactness.
- Notification rendering tests prove protected task/reminder text is shown only when locally decrypted and allowed by platform settings, and that generic/private notifications are used when content is hidden, unavailable, or undecryptable.

### Privacy and security proof

- Tests or documented inspections prove no protected task/reminder data is written to plaintext storage, SharedPreferences, logs, crash breadcrumbs, analytics, CI artifacts, notification caches, backup-included files, or unencrypted support/debug output.
- Android backup policy excludes encrypted databases, WAL/journal/schema/FTS sidecars, key-wrapper storage, nonce ledgers, pairing state, attachment caches if any, tokens, key material, logs, and screenshots unless a later accepted recovery design supersedes this boundary.
- Network-egress tests prove no task data or protected reminder data leaves the trusted Android device. Ideally this includes an instrumentation or proxy-based check while creating, editing, completing, deleting, and reminding tasks.
- Key-unavailable, key-invalidated, unsupported-cipher, corruption, migration failure, and setup-latency paths are typed, user-visible where appropriate, fail closed, and do not overwrite encrypted data or fall back to plaintext.

### Documentation and human acceptance

- Known limitations are documented plainly, including no account/sync/recovery, local-only data, possible data loss if device/app data is lost, exact-alarm OS limitations, notification permission limitations, no WearOS, and no public service availability.
- ADB install and uninstall/reinstall expectations are documented.
- Paul performs separate real-device human acceptance of the exact artifact and workflow before the release is described as live or usable.

## Dependency-ordered implementation slices

These slices are named for future work only. This document does not implement them or move unrelated Notion tasks.

1. Android local domain foundation with fixture-only storage
   - Create the smallest Android module/domain model/repository boundary needed for one Inbox/list and task lifecycle tests.
   - Use only synthetic fixture data or volatile in-memory storage.
   - Acceptance: domain tests for create/view/edit/complete/undo/delete and reminder value rules pass without persisting real user data.

2. Encrypted Android persistence and key bootstrap
   - Implement the accepted SQLCipher/Room/Keystore-backed local store and invisible first-run bootstrap.
   - Acceptance: storage, migration, backup-exclusion, key capability, and fail-closed tests prove no plaintext persistence for real task/reminder data.

3. Android first-launch Inbox workflow
   - Implement the phone UI for immediate local task capture, list/detail/edit, completion/undo, deletion, empty/error states, and light/dark presentation.
   - Acceptance: instrumentation tests satisfy the quick-start metric and core workflow evidence while offline.

4. Local exact reminder and notification workflow
   - Implement exact reminder entry, notification permission timing, exact-alarm scheduling, local notification rendering, degraded states, and encrypted outbox-compatible local mutation boundaries.
   - Acceptance: instrumentation/tests cover success, permission denial, exact-alarm unavailable/revoked, reboot, time-zone change, missing keys, private/generic rendering, and no protected data leakage.

5. Dogfood artifact and install evidence
   - Produce the signed or explicitly debug/dogfood Android artifact and update install/release notes.
   - Acceptance: deterministic build, ADB install proof, repository policy/CI green, changed-file manifest, known limitations, and release-state handoff are reviewable.

6. Paul's real-device human acceptance
   - Paul installs the exact artifact and separately accepts or rejects the workflow on a real Android phone.
   - Acceptance: human acceptance is recorded without reclassifying technical evidence as live usage.

The first future slice is intentionally limited to Android local domain foundation with fixture-only storage. It can be independently verified without real user data and without implementing encrypted persistence, product networking, sync, server APIs, reminders, or Android release artifacts.
