# ADR-0001 — E2EE is mandatory for the first usable release

Status: Accepted  
Date: 2026-08-02  
Decision owner: Paul

## Context

Privacy from the service operator is a defining product promise. Users should be able to trust that task titles, descriptions, projects, comments, reminders and attachments cannot be read by the hosted service unless a user explicitly shares access with another authorised user.

Adding end-to-end encryption after launch would force incompatible changes across identity, local persistence, sync, sharing, search, reminders, recovery and API design. It would also risk shipping a data model whose privacy assumptions cannot be corrected cleanly.

## Decision

Phase 1 end-to-end encryption is mandatory for the first usable release.

A first usable release means any build intended to store or synchronise real user task data for dogfooding, alpha testing or wider use. Disposable technical prototypes may use fixture data without E2EE, but they must not be treated as usable releases or connected to production user data.

The service must not possess the keys needed to decrypt protected user content. Account authentication and password reset must remain separate from content-key recovery.

## Required consequences

- The privacy threat model and server-visible metadata boundary are Phase 1 blockers.
- Cryptographic envelopes, device provisioning, recovery, sharing, revocation, local storage and sync must be designed around E2EE from their first production implementation.
- Task content, project and list names, labels, comments, reminder details and attachments are encrypted before leaving a trusted client.
- Search, recurrence evaluation and meaningful reminder text are handled on trusted clients.
- Sharing grants access by wrapping space keys for authorised users or devices; the server does not decrypt the content key.
- Feature tasks cannot move to Ready while an unresolved E2EE question would change their data contract, security model or user-visible privacy behaviour.

## Accepted limitations

- The server will still observe the minimum metadata needed for accounts, routing, membership, billing, abuse prevention and synchronisation. The threat-model task must enumerate and justify it.
- Revoking a user or device cannot erase content it previously decrypted.
- Losing all authorised devices and recovery material may make encrypted data permanently unrecoverable.

## Alternatives considered

### Add E2EE after the first release

Rejected because it would create migration risk, incompatible assumptions and a period in which the core privacy promise is false.

### Server-managed encryption keys

Rejected because the service could decrypt content and therefore could not honestly claim end-to-end encryption.

## Affected work

- Privacy threat model and metadata boundary
- E2EE protocol and encrypted entity envelope
- Device identity, provisioning and secure key storage
- Recovery and encrypted key backup
- Sharing, membership changes and key rotation
- Encrypted local persistence and offline sync
- Private search, recurrence, reminders and notifications
