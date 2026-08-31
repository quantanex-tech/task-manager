# Repository structure and workflow foundation

The repository is a monorepo for the Android app, WearOS app, backend, shared protocol fixtures, infrastructure and documentation. Keeping these surfaces together makes each production change reviewable with its tests, migrations, contracts and ADRs.

## Boundaries

| Area | Path | Notes |
| --- | --- | --- |
| Android local workspace | `android/` | Current Gradle workspace for `android/app`, `android/domain`, and `android/persistence`. Slice 3's phone app candidate lives here with bounded Compose UI and encrypted local Inbox persistence. It is not deployed, installable-proven, a reminder scheduler, sync implementation, or final release artifact. |
| Android phone app | `android/app/` | Single-activity Jetpack Compose phone app candidate. Production task state must open through `EncryptedTaskRepositoryFactory`; no network permissions, telemetry, plaintext fallback, sync, accounts, or reminder scheduling in Slice 3. |
| WearOS app | `apps/wearos/` | Gradle module, watch debug APKs and wearable UX tests once created. |
| Backend server | `server/` | Runtime services, API handlers, persistence and server tests once created. |
| Shared contracts | `packages/protocol-test-vectors/` | Encrypted envelope schemas, sync/event fixtures and compatibility vectors. |
| Infrastructure | `infra/docker/` | Docker-first development and self-hosted deployment definitions. |
| Product/spec docs | `docs/product/specs/` | Reviewed task specs copied or linked from Notion when implementation starts. |
| ADRs | `docs/adr/` | Accepted architecture decisions. |
| GitHub automation | `.github/` | PR/issue templates, CODEOWNERS and workflow checks. |

Directories that do not yet contain implementation files may be absent until their first Ready task creates them. Do not collapse these boundaries to make an early change smaller if it would hide deployable code, infrastructure, protocol contracts or documentation from review.

The earlier reserved `apps/android/` path remains a documented monorepo boundary for any future migration or additional deployable Android surface; Slice 3 deliberately keeps the approved bounded app module at `android/app` beside the domain and persistence modules.

## Branch protection policy

The default branch should require pull requests, at least one approving review, resolved conversations and required status checks. Direct unvalidated production changes are out of scope for the project workflow.

Required checks start with `Repository policy validation` and should expand as code surfaces arrive: server tests, Android/WearOS Gradle builds, Docker Compose validation, secret scanning and release artifact checks.

## Secret policy

Secrets live in ignored local files or managed secret stores, never in Git. See `SECURITY.md` and `.gitignore` for the initial policy.
