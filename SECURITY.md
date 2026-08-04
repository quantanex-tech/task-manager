# Security policy

The project is privacy-first. End-to-end encryption is mandatory before any usable release stores or synchronises real user task data; see `docs/adr/0001-phase-1-e2ee-mandatory.md`.

## Reporting vulnerabilities

Until a dedicated security address is published, report vulnerabilities privately to Paul through the established project communication channel and include enough detail to reproduce and triage the issue. Do not open public issues for exploitable findings involving authentication, E2EE, sync integrity, secrets, deployment or data exposure.

When a dedicated address or GitHub private vulnerability reporting is enabled, this file should be updated in the same change.

## Secret handling

Never commit real secrets or protected user data, including:

- local `.env` files;
- Android signing keys, keystores, Play credentials or release artifacts containing secrets;
- server deployment credentials, database URLs, S3 credentials or backups;
- Notion, GitHub, analytics, email or other third-party API tokens;
- recovery secrets, plaintext protected fixtures or production task content.

Use local ignored files for development, GitHub Environments/Secrets for automation, least-privilege tokens and sanitized logs/artifacts.

## CI and fork safety

GitHub Actions should use minimal `GITHUB_TOKEN` permissions, pinned third-party actions, no plaintext protected fixtures, restricted access to release/deployment/signing secrets and short artifact retention. Workflows triggered from untrusted forks must not receive protected secrets.

## E2EE boundary

Server examples, tests and docs must not add a server-readable content-key path or plaintext production-data shortcut. Disposable prototypes may use fixture data only when they are clearly marked as non-usable releases.
