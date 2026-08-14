# Contributing

This repository uses Notion for product approval and GitHub for source control, review, CI and releases.

## Source of truth before work starts

1. Fetch the Notion brief and the `To do list app` project page.
2. Query the Main Tasks DB using the preserved `Collection = To do list app` relation filter.
3. Select only work whose Notion `Status` is `Ready`.
4. Re-fetch the selected task page immediately before making changes.
5. Move the task to `In progress` before implementation starts.

Do not specify or build `Idea` items. `Todo` items are for specification/review only.

## Branch and pull-request policy

- Branch from `main` using a focused name such as `task/<short-description>`.
- Keep one primary task per branch unless the Notion tasks are explicitly independent.
- Open a pull request before merging to `main`; direct pushes to `main` are not part of the delivery process.
- Required checks must pass before merge.
- At least one approving review is required unless Paul explicitly changes the repository policy.
- Resolve all review conversations before merge.
- Prefer squash merges for task branches until release automation defines a different strategy.

## Local verification

Run the repository policy smoke check before opening a PR:

```bash
./scripts/check-repository-policy.sh
```

As implementation surfaces are added, also run the relevant commands for formatting, linting, unit/integration tests, Docker Compose validation, Android debug builds and WearOS debug builds. Update the PR body with the exact commands and results.

## Specs, ADRs and migrations

- Update implementation specs in `docs/product/specs/` when behavior, user flows, data contracts or test plans change.
- Record accepted architecture decisions in `docs/adr/` and link them from the Notion ADR index in the handoff.
- Never edit a migration that may already have been applied to live data; add a new forward migration instead.
- Keep encrypted protocol fixtures and sync/event contracts deterministic and reviewable.

## Security, privacy and E2EE review

Every PR should state whether it affects any of:

- server-visible metadata boundaries;
- E2EE key, envelope, recovery or sharing flows;
- offline sync, conflict handling or tombstones;
- migrations or protected fixtures;
- Android/WearOS install, signing or artifact handling;
- deployment, self-hosting, logging or secrets.

No production credentials, signing keys, Notion tokens, database dumps, S3 credentials, Play credentials or plaintext protected user content may be committed.

## Follow-ups

Unplanned follow-ups belong in Notion as new `Idea` items with the `Idea` tag. Do not silently expand a Ready task beyond its reviewed scope.
