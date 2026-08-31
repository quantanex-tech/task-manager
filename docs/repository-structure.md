# Repository structure and workflow foundation

The repository is a monorepo for the Android app, WearOS app, backend, shared protocol fixtures, infrastructure and documentation. Keeping these surfaces together makes each production change reviewable with its tests, migrations, contracts and ADRs.

## Boundaries

| Area | Path | Notes |
| --- | --- | --- |
| Android fixture-only domain workspace | `android/` | Current Gradle workspace for the pure Kotlin/JVM `android/domain` module. It is volatile/in-memory and not an Android app, encrypted persistence implementation or release artifact. |
| Android phone app | `apps/android/` | Gradle module, debug APKs and phone-specific UI tests once created. |
| WearOS app | `apps/wearos/` | Gradle module, watch debug APKs and wearable UX tests once created. |
| Backend server | `server/` | Runtime services, API handlers, persistence and server tests once created. |
| Shared contracts | `packages/protocol-test-vectors/` | Encrypted envelope schemas, sync/event fixtures and compatibility vectors. |
| Infrastructure | `infra/docker/` | Docker-first development and self-hosted deployment definitions. |
| Product/spec docs | `docs/product/specs/` | Reviewed task specs copied or linked from Notion when implementation starts. |
| ADRs | `docs/adr/` | Accepted architecture decisions. |
| GitHub automation | `.github/` | PR/issue templates, CODEOWNERS and workflow checks. |

Directories that do not yet contain implementation files may be absent until their first Ready task creates them. Do not collapse these boundaries to make an early change smaller if it would hide deployable code, infrastructure, protocol contracts or documentation from review.

## Branch protection policy

The default branch should require pull requests, at least one approving review, resolved conversations and required status checks. Direct unvalidated production changes are out of scope for the project workflow.

Required checks start with `Repository policy validation` and should expand as code surfaces arrive: server tests, Android/WearOS Gradle builds, Docker Compose validation, secret scanning and release artifact checks.

## Secret policy

Secrets live in ignored local files or managed secret stores, never in Git. See `SECURITY.md` and `.gitignore` for the initial policy.
