# Task Manager

Open-source, privacy-first task manager intended to begin with practical Todoist-like core workflows and then develop its own strengths.

## Current status

Product specification and task review are managed in Notion. App/server implementation should only begin when the relevant Notion task is moved to `Ready`.

The first server-side foundation is Docker-first: PostgreSQL, MinIO and a Go + Chi server can be run through Docker Compose. See [`docs/infrastructure.md`](docs/infrastructure.md).

Canonical planning sources:

- Product brief: Notion page `3b0319db76c8812887fef76b5243cfc4`
- Project / Collection: Notion page `3a3319db76c8805ba2b4f5d88aae05af`
- Main Tasks DB: Notion database `d29c181219e34cafa4bdd9ea5efe26f5`
- ADR index: Notion page `3b0319db76c88188b99ed824ec45a9dd`

## Product constraints

- Android and WearOS are the first client targets.
- Server-side development, test dependencies, hosted deployment and self-hosting are Docker-first.
- GitHub Actions provide repeatable validation and release artifacts as implementation surfaces arrive.
- End-to-end encryption is mandatory before any usable release stores or syncs real user task data. See [`docs/adr/0001-phase-1-e2ee-mandatory.md`](docs/adr/0001-phase-1-e2ee-mandatory.md).
- Personal/core usage should remain fully useful for free; commercial value should come from teams/businesses with more than two users.

## Repository layout

The repository is intentionally a monorepo so every production change can be reviewed with its tests, infrastructure, migrations, protocol contracts and ADRs.

| Path | Purpose |
| --- | --- |
| `apps/android/` | Future Android phone app, Gradle module and debug/release build outputs. |
| `apps/wearos/` | Future WearOS companion app, Gradle module and watch install artifacts. |
| `server/` | Future backend application code and server tests. |
| `packages/protocol-test-vectors/` | Future encrypted envelope, sync/event and compatibility fixtures. |
| `infra/docker/` | Future Docker-first local/self-hosted stack, Compose files and persistence notes. |
| `docs/adr/` | Accepted architecture decision records. ADR-0001 is already committed here. |
| `docs/product/specs/` | Future reviewed implementation specs copied or linked from Notion. |
| `.github/` | Pull-request templates, issue templates and CI policy checks. |

Implementation tasks may create these directories when they add real files. Keep deployable code, shared contracts, infrastructure and documentation separated along these boundaries.

## Development workflow

1. Fetch the Notion brief and project page.
2. Query Main Tasks DB for `Collection = To do list app`.
3. Work only on tasks whose `Status = Ready`.
4. Re-fetch the selected task page immediately before working.
5. Use a feature branch per task.
6. Follow test-first development where behaviour is specified.
7. Update docs/ADRs in the same PR as code that depends on them.
8. Do not mark a task `Done` unless acceptance criteria, tests, docs and delivery requirements are satisfied.

See [`docs/notion-delivery-workflow.md`](docs/notion-delivery-workflow.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Required checks

Before opening or updating a pull request, run the repository policy smoke check:

```bash
./scripts/check-repository-policy.sh
```

As implementation surfaces arrive, add the relevant local commands here and to CI: formatting, linting, server tests, Android/WearOS Gradle builds, Docker Compose validation and secret scanning.

## Server development quickstart

Prerequisite: Docker with Docker Compose v2.

```bash
git clone https://github.com/quantanex-tech/task-manager.git
cd task-manager
./scripts/dev.sh up
curl http://127.0.0.1:8080/health/ready
```

Run the containerised smoke test stack:

```bash
./scripts/dev.sh test
```

## Clone and future Android/WearOS install flow

From a laptop with Android Platform Tools installed:

```bash
git clone https://github.com/quantanex-tech/task-manager.git
cd task-manager
```

Once Android/WearOS Gradle modules exist, the intended debug install flow is:

```bash
# Android phone
./gradlew :android:assembleDebug
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# WearOS watch; replace SERIAL with the watch device ID from adb devices
./gradlew :wear:assembleDebug
adb -s SERIAL install -r wear/app/build/outputs/apk/debug/wear-debug.apk
```

These commands are documented now so implementation tasks can preserve an easy clone/build/install path for Paul’s laptop. See [`docs/android-adb-install.md`](docs/android-adb-install.md).

## Security and secrets

Do not commit real credentials, production data, signing keys or protected E2EE fixtures. Use local `.env` files, GitHub environments/secrets and sanitized CI logs/artifacts. See [`SECURITY.md`](SECURITY.md).

## Versioning and changelog

Until the first usable release, use `0.0.0`-style pre-release tags only when Paul explicitly asks for an artifact. Once releases begin, maintain [`CHANGELOG.md`](CHANGELOG.md) using Keep a Changelog categories and semantic-version tags (`vMAJOR.MINOR.PATCH`) unless an ADR replaces this policy.

## License

License selection is still pending. Do not assume a license until it is recorded in a Notion task/ADR and committed here.
