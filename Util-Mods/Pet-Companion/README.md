# Pet Companion

Early implementation of the persistent AI pet system specified by `minecraft_ai_pet_system_implementation_handoff.md`. The handoff is binding; completion evidence and unresolved decisions are tracked in `IMPLEMENTATION_PROGRESS.md`.

## Modules

- `pet-common` — pure Java authoritative domain types and transition rules.
- `pet-service` — central persistence/orchestration service (under construction).
- `pet-fabric` — Minecraft 1.21.10 Fabric gameplay integration (under construction).
- `pet-velocity` — isolated whole-network presence producer for Velocity 3.2.
- `db/migrations` — versioned MariaDB schema migrations.
- `deploy` — standalone service/systemd installation and rollback templates.

Automatic pet carry now uses the existing `MCServerPortals` source hook and its unchanged
canonical signed `MC-Portal-Protocol` request. The source backend reserves authority before the
portal request; the final pet-enabled backend claims that persisted reservation on join. The
waiting lobby cannot claim it because its backend ID is not the recorded final destination.
Presence is deliberately packaged as the separate `pet-velocity`
plugin: it observes only proxy login/disconnect and cannot change admission, lobby, or portal
routing. The central service now implements signed webhook convergence, UUID-bound opaque links,
and server-created hosted Checkout without a website account or manual code entry. `/pet portal`
now creates a short-lived hosted Stripe Customer Portal link from the verified customer binding;
live Stripe test-mode delivery remains open.

## Local verification

From PowerShell in this directory:

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat :pet-service:installDist
```

The runnable service distribution is written to `pet-service/build/install/pet-service`.
It validates environment-only authentication/database settings, exposes authenticated
`/health/live` and `/health/ready`, and does not apply migrations on startup. See
`deploy/README.md` and `config/pet-service.env.example` before running it.

The baseline is Java 21, Gradle 8.14, Minecraft 1.21.10, Yarn `1.21.10+build.1`, Fabric Loader 0.17.3, Fabric API `0.138.0+1.21.10`, and Loom 1.11.8. Runtime deployment versions still require confirmation before integration rollout.

Implemented player commands currently include `/pet`, `/pet status`, `/pet link`, `/pet portal`,
`/pet adopt <cat|dog> <name>`, `/pet place`, `/pet pickup`, `/pet recall`, and
`/pet compass`. Operators additionally have permission-separated `/pet admin inspect <ownerUuid>`,
`recover <ownerUuid>`, `recall-reset <ownerUuid>`, and loaded-only `reconcile` operations. Website
consumption of `/pet link`, an adapter for
the deployment's selected permission-provider mod, and admin commands remain tracked work.

Command gates use explicit nodes: `aipets.use`, `aipets.adopt`, `aipets.chat`,
`aipets.compass`, and `aipets.recall`. The admin namespace reserves
`aipets.admin.inspect`, `.recover`, `.subscription`, `.memory`, and `.reconcile`.
Without a permission-provider adapter, player nodes default to allowed and admin nodes require
vanilla permission level 3. A provider adapter can install a checker through `PetPermissions`.

The central service evaluates persisted sleep deadlines in bounded batches every 30 seconds.
Its state machine uses UTC instants and fixed rules (30-minute whole-network absence,
23-hour maximum awake time, and one-hour sleep). Install the built `pet-velocity` JAR on the
proxy and configure its environment as described in `config/pet-velocity.env.example`; backend
switch events are intentionally not subscribed and therefore do not start logout sleep.

For supported ServerPortals switches, a materialized pet within 16 blocks is atomically reserved
for five minutes, discarded on the source only after the reservation commits, and reconstructed
with its exact stored appearance only after the final backend claims the matching transfer ID.
Far and manually held pets remain unchanged. The service scans expired reservations at startup
and every five seconds, returning abandoned transfers to `HELD` in bounded batches.

Missing local representations are reconstructed lazily from authoritative state when the owner is
online and the recorded chunk is already loaded. Join and bounded five-second checks re-read
authority after reconciling loaded candidates, reuse the exact expected UUID when present, discard
stale saved representations, and recreate the persisted variant/scale only when still absent. This
path never requests or force-loads an unloaded chunk.

Placement also has an owner-scoped in-flight gate: while one `/pet place` request is waiting on
authority, simultaneous requests are answered locally and cannot prepare a second entity or make a
second authority mutation. Durable service idempotency and CAS remain the final cross-process
arbiter after the local gate.

Recall invalidates the prior backend/entity identity in authority before destination spawn. If an
offline source later loads an old saved entity, ordinary entity-load reconciliation sees the
backend/UUID mismatch and discards it; repeated old chunk saves cannot displace or duplicate the
authoritative recalled representation.

Provider-neutral long-term vector contracts, pet-scoped retrieval, durable embedding jobs, and
relational full-reindex orchestration are implemented. The selected deployment is self-hosted
Qdrant on the Raspberry Pi with OpenAI `text-embedding-3-small` for compact-card embeddings;
MariaDB remains authoritative. Endpoint, secret delivery, model worker, and staging validation
still require configuration; see `docs/VECTOR_OPERATIONS.md` for invariants and the verification
procedure.

The bounded service-side dialogue core is also implemented: pre-call access/sleep/safety gates,
token-budgeted prompts, strict structured output, admission/cost/circuit controls, and transactional
event/usage/trait auditing. Fabric private-chat input and the bounded billboard-like speech display
are implemented; production still needs a selected model/tokenizer adapter and central dialogue
gateway wiring. See `docs/DIALOGUE_CORE.md`.

Do not put database, model-provider, Stripe, vector, or internal-service secrets in this repository.
Deployment security, firewall, secret rotation, and checksum-verified MariaDB restore procedures
are documented under `deploy/`, `docs/SECRET_MANAGEMENT.md`, and
`docs/DATABASE_BACKUP_RESTORE.md`; the templates must be reconciled with the real host before use.
