# Architecture decision records

Accepted ADRs are canonical in this directory once committed. Notion can remain the planning/index surface, but implementation changes should link to the committed ADR files.

| ADR | Status | Summary |
| --- | --- | --- |
| [ADR-0001](0001-phase-1-e2ee-mandatory.md) | Accepted | E2EE is mandatory for the first usable release. |
| [ADR-0002](0002-encrypted-client-sqlite-persistence.md) | Accepted | Android and WearOS use Room + SQLCipher encrypted SQLite behind a replaceable repository boundary. |

## Process

1. Draft material may start in Notion while decisions are being reviewed.
2. Once accepted, commit the ADR under `docs/adr/` with an immutable number and status.
3. Link the committed file from related specs, PRs and handoffs.
4. Supersede decisions with a new ADR rather than rewriting accepted history.

The Notion ADR index remains the planning source of truth until ADR publication is fully automated. Repository ADRs should link back to Notion task IDs where relevant.
