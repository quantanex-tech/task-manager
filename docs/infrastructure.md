# Docker-first infrastructure

This repository uses Docker as the default execution boundary for the server-side system. Native Android and WearOS apps remain Gradle-built clients and connect to this stack during development.

## Quickstart

Prerequisite: Docker with Docker Compose v2.

```bash
git clone https://github.com/quantanex-tech/task-manager.git
cd task-manager
./scripts/dev.sh up
curl http://127.0.0.1:8080/health/ready
```

The first `up` copies `.env.example` to `.env` if needed, starts PostgreSQL and MinIO, runs migrations, creates the configured object bucket, and starts the server.

Local URLs:

- Server: `http://127.0.0.1:8080`
- Live health: `http://127.0.0.1:8080/health/live`
- Readiness health: `http://127.0.0.1:8080/health/ready`
- MinIO console: `http://127.0.0.1:9001`

## Common commands

```bash
./scripts/dev.sh up          # start dependencies, migrate, start server
./scripts/dev.sh migrate     # run migrations and ensure the object bucket exists
./scripts/dev.sh test        # build images and run the containerised smoke stack
./scripts/dev.sh logs        # follow server logs
./scripts/dev.sh down        # stop containers, keep volumes
./scripts/dev.sh backup      # local PostgreSQL + MinIO backup under backups/
./scripts/dev.sh reset --yes-local
```

`reset --yes-local` deletes local project volumes. It is deliberately explicit so production or shared environments are not deleted by accident.

## Compose services

- `postgres`: PostgreSQL 16 with persistent project-namespaced volume.
- `minio`: S3-compatible object storage with persistent project-namespaced volume.
- `server`: non-root Go + Chi modular monolith runtime image.
- `migrate`: tool profile entrypoint that applies embedded SQL migrations and ensures the MinIO bucket exists.
- `smoke`: test profile entrypoint that waits for readiness, writes/reads a database probe, and writes/reads an object-storage probe.

## Android and WearOS client connectivity

The local stack exposes the server on the host at `http://127.0.0.1:8080`.

When Android/WearOS modules exist:

- Android emulator should use `http://10.0.2.2:8080` to reach the host stack.
- Physical Android devices on the same network should use the host machine LAN IP and port 8080.
- WearOS companion testing should use the phone/emulator path documented by the client task, not a server-readable shortcut.

Real task/list/project/comment/reminder/attachment content must still follow ADR-0001: encrypt on trusted clients before leaving the client. This infrastructure task does not make production user data safe by itself.

## Self-host preview boundary

Self-hosting is supported as a preview topology here, but production use with real user data requires later E2EE, backup/restore, release-image, secret-management and operational hardening tasks.

Current policy decisions from the Notion task:

- Use MinIO for S3-compatible object storage for now.
- Use Go, net/http and Chi as a modular monolith.
- Recommend bring-your-own reverse proxy and TLS rather than bundling a proxy in this baseline stack.
- Seed only minimal synthetic disposable fixtures until the E2EE protocol and core domain model are accepted.

Before using with real user data, replace all development defaults in `.env`, run behind TLS, pin image versions, configure backups, and use clients that enforce end-to-end encryption.
