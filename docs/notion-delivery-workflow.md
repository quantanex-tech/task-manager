# Notion delivery workflow

The Notion brief and Main Tasks DB are the planning source of truth until implementation work is represented in this repository.

## Required cycle

At the start and end of every work cycle:

1. Fetch the product brief and project page.
2. Query the Tasks view of the Main Tasks DB, preserving the filter `Collection = To do list app`.
3. Re-fetch the selected task page immediately before working on it.
4. Update status/progress in Notion as work advances.
5. Re-query the database before selecting the next item because Paul or another agent may have changed priorities or approval state.

## Backlog state machine

- `Idea` status + `Idea` tag: proposal only. Do not specify or build.
- `Idea` status without `Idea` tag: Paul has approved it. Change it to `Todo` and create the specification.
- `Todo`: write a complete specification. Do not code yet.
- `Ready`: sufficiently specified and authorised for development.
- `In progress`: implementation has started. Work on one primary item at a time unless explicitly independent.
- `Done`: acceptance criteria pass, automated tests pass, documentation is updated and the work is merged or otherwise delivered.
- `Live`: completed change has been deployed to production.
- `Won't do` / `Don't need` / `Not Possible`: stop work and preserve reasoning on the page.

## Selection order

1. `Ready` + `Phase 1`.
2. Other `Ready` items whose dependencies are satisfied.
3. `Todo` + `Phase 1` to specify.
4. Approved ideas: `Status = Idea` with no `Idea` tag.
5. Other `Todo` items.
6. Never select an item that still carries the `Idea` tag.

## Definition of Ready

A task can move from `Todo` to `Ready` only when it contains:

- problem statement and intended user outcome;
- functional requirements and explicit non-goals;
- user flows and important error or empty states;
- data model changes and migration notes;
- API or event contracts where relevant;
- offline, sync and conflict behaviour;
- privacy, security and E2EE implications;
- notification, recurrence, locale and time-zone behaviour where relevant;
- accessibility expectations;
- dependencies and sequencing;
- testable acceptance criteria;
- unit, integration and end-to-end test expectations;
- unresolved questions clearly identified.

If an unresolved question changes security, data compatibility, billing, user-visible behaviour or architecture, keep the item at `Todo` and ask Paul.

## Definition of Done

- All acceptance criteria are demonstrably satisfied.
- New behaviour has automated tests at the appropriate layers.
- Existing tests, linting, type checks and builds pass.
- Accessibility and responsive behaviour have been checked where relevant.
- Security and privacy effects have been reviewed.
- Data migrations are reversible or have a documented recovery route.
- User-facing and developer documentation are updated.
- The task page contains a concise implementation summary, key decisions, links to code or pull requests, and follow-up ideas.
- Any unplanned follow-up is added as a new `Idea` item with the `Idea` tag rather than silently expanding scope.
