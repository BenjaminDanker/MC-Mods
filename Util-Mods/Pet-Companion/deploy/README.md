# Pet service deployment checkpoint

This directory is a template, not authorization to deploy. Confirm the service host, MariaDB database/schema, backup path, private routing, and firewall policy before installation. Do not expose port 8787 publicly.

## Build and install

Build the application distribution without embedding secrets:

```powershell
.\gradlew.bat :pet-service:installDist
```

Copy the contents of `pet-service/build/install/pet-service/` to `/opt/pet-companion/`. Create a dedicated `pet-companion` system user with no interactive login. Copy `config/pet-service.env.example` to `/etc/pet-companion/pet-service.env`, set mode `0600`, ownership `pet-companion:pet-companion`, and supply secrets through the approved host mechanism.

Apply every migration in `db/migrations/` in version order, including V001 through V003, using the operator-controlled process in `db/README.md`. The service never changes schema on startup.

Install `deploy/systemd/pet-companion.service`, run `systemd-analyze verify` and
`systemctl daemon-reload`, then stage with `systemctl enable --now pet-companion`. Wildcard IPv4
and IPv6 binds are rejected. If the service binds a private address instead of loopback, merge the
reviewed `deploy/firewall` rules so TCP 8787 accepts only known Fabric, Velocity, reverse-proxy,
and HTTPS reverse-proxy addresses.

## Environment variables

- `PET_SERVICE_TOKEN`: required 32–512 character internal bearer token.
- `PET_DB_JDBC_URL`: required `jdbc:mariadb://host:port/database` URL without embedded credentials.
- `PET_DB_USER`, `PET_DB_PASSWORD`: required database credentials.
- `PET_SERVICE_BIND_ADDRESS`, `PET_SERVICE_PORT`: internal listener; defaults `127.0.0.1:8787`.
- `PET_SERVICE_BACKLOG`, `PET_SERVICE_WORKER_THREADS`, `PET_SERVICE_WORKER_QUEUE`: bounded HTTP admission/execution settings.
- `PET_SERVICE_SHUTDOWN_GRACE_SECONDS`: graceful stop interval.
- `PET_ALLOWED_SPECIES`: comma-separated subset of `minecraft:cat,minecraft:wolf`; defaults to both and rejects arbitrary entity IDs.
- `PET_RAW_TEXT_RETENTION_DAYS`: raw player/pet dialogue redaction window, range 1–30 days and default 7; compact summaries/source references remain.
- `PET_AI_ENABLED`: global AI kill switch; set to `false` and restart to deny dialogue/consolidation while preserving physical pet authority, adoption, placement, sleep, and recall. Defaults to `true`.
- `PET_STRIPE_ENABLED`: opt-in billing/webhook/link runtime; defaults to `false` so local development does not require billing secrets.
- `PET_STRIPE_PRICE_ID`: exact recurring Stripe Price ID accepted for AI entitlement when billing is enabled.
- `PET_STRIPE_SECRET_KEY`: environment-specific `sk_test_*`/`sk_live_*` secret used only for outbound server-side Checkout Session creation.
- `PET_STRIPE_WEBHOOK_SECRET`: environment-specific `whsec_*` endpoint secret. Never reuse a test secret in production.
- `PET_PUBLIC_BASE_URL`: HTTPS origin hosting the public `/checkout/*` proxy routes; paths, queries, fragments, and HTTP are rejected.
- `PET_ACCOUNT_LINK_PEPPER`: unique random 32–512 character HMAC key; keep it distinct from the service bearer token and Stripe secret. Rotation invalidates outstanding opaque links.
- `PET_STRIPE_PAYMENT_GRACE_DAYS`: failed-payment grace, 0–14 days; defaults to 3.
- `PET_ACCOUNT_LINK_TTL_MINUTES`: one-time `/pet link` lifetime, 2–30 minutes; defaults to 10.
- `PET_DB_POOL_MAXIMUM`, `PET_DB_POOL_MINIMUM_IDLE`: bounded pool sizes, default 4/1 and hard maximum 16.
- `PET_DB_CONNECTION_TIMEOUT_MS`, `PET_DB_VALIDATION_TIMEOUT_MS`: bounded database waits.
- `PET_QDRANT_ENABLED`: opt-in Qdrant vector index; defaults to `false` and must remain disabled until the endpoint is staged.
- `PET_QDRANT_URL`, `PET_QDRANT_COLLECTION`, `PET_QDRANT_DIMENSION`: Qdrant origin, collection, and fixed embedding dimension.
- `PET_QDRANT_API_KEY`, `PET_QDRANT_TIMEOUT_MS`: optional Qdrant API key and bounded request timeout; the key is never logged.
- `PET_OPENAI_API_KEY`: required when the OpenAI embedding worker is enabled; never place it in properties, logs, or Qdrant payloads.
- `PET_OPENAI_BASE_URL`, `PET_EMBEDDING_MODEL`, `PET_EMBEDDING_DIMENSION`: bounded OpenAI endpoint/model/dimension settings; the default model is `text-embedding-3-small` at 1536 dimensions.

Install the service environment as `root:pet-companion` mode `0640` or stricter. See
`docs/SECRET_MANAGEMENT.md` for the complete secret inventory and coordinated rotation order.

Fabric backends use `config/pet-companion.properties` and name their token environment variable with `authority.token-environment`. Set that variable to the same internal token without writing the token into the properties file. Also set the environment variable named by `authority.compass-signing-environment` to one persistent, distinct 32+ character signing secret shared by every pet-enabled backend; rotating it invalidates existing compasses, which `/pet compass` can replace. Configure every backend's player-facing label in `authority.backend-friendly-names`.

## Health and rollback

All health routes require the bearer token. `/health/live` reports process liveness.
`/health/ready` reports database reachability and requires all 16 V001 InnoDB tables plus
V002's recall replay and V003's Checkout-link columns; Stripe reports `CONFIGURED` only when its validated local
configuration is enabled (this is not a provider-reachability probe), while vector/model remain
degraded. `/health/metrics` exports Prometheus text with bounded names and no owner, pet,
conversation, authorization, or raw-text labels. Keep it on the same backend-only network
boundary; it is not a public endpoint.

Gameplay backends use authenticated `/v1/pets` authority reads/physical mutations (including monthly recall and recall-failure compensation) and `/v1/adoptions` subscription-gated adoption on the same private listener. These routes are not public APIs and must remain behind the backend-only firewall policy.

Admin commands are permission-separated under `/pet admin`. Recovery atomically moves a non-held
pet to `HELD` without changing its persistent identity/appearance, then queues loaded entities for
normal authority reconciliation. Recall reset releases only the current UTC month marker and
retains its historical audit row. The supporting `/v1/admin/*` route is backend-only and must never
be added to the public reverse proxy.

When Stripe is enabled, Fabric `/pet link` calls authenticated
`POST /v1/account-links/{minecraft-uuid}`. The service returns a clickable HTTPS URL containing a
256-bit opaque token, stores only its HMAC digest, invalidates prior unconsumed tokens for that
UUID, expires link opening after the configured TTL, and permits at most three generations per
ten minutes. The player types nothing: public `GET /checkout/{token}` resolves the UUID
server-side, creates an idempotent subscription-mode Stripe Checkout Session using the configured
Price, persists its session ID, and redirects with HTTP 303 to `checkout.stripe.com`. Never expose
the service bearer token or raw UUID in the public link. Reopening a valid link reuses Stripe's
digest-derived idempotency key; the signed Checkout webhook atomically consumes the matching link.

Stripe sends raw JSON to `POST /v1/stripe/webhook`. It requires a current valid
`Stripe-Signature` HMAC. Keep port 8787 private and publish only that exact path, the bounded
opaque Checkout route, and inert success/cancel pages through the supplied HTTPS reverse proxy;
keep request-body rewriting/decompression disabled and the same 256 KiB webhook limit. Checkout completion binds known
customer/subscription IDs but grants nothing; subscription/invoice lifecycle events produce the
access flag, reject the wrong Price, retain cancel-at-period-end access through the period, and
apply the configured failed-payment grace. Use separate test/live endpoint secrets and Price IDs.
The pet service now creates hosted subscription-mode Checkout and attaches the server-resolved
UUID as trusted Session/subscription metadata; no website form, account, or manual code entry is
required. Fabric `/pet portal` resolves the connected player's UUID to the Stripe customer ID
previously bound by a verified webhook, then returns Stripe's short-lived hosted billing-management
URL; no customer ID or code is shown or entered. Configure the Customer Portal separately in each
Stripe test/live environment. The exact public `/checkout/return` page is inert; subscription
changes still take effect only through verified webhooks. Use
`deploy/nginx/pet-stripe-webhook.conf.example` only after replacing its hostname/certificates; it
suppresses token-path access logs and returns 404 for internal bearer routes.

Automatic carry requires matching Pet Companion builds on gameplay backends and the accompanying
`MCServerPortals` build containing the optional pre-transfer hook. Roll these two artifacts to one
staging route together. The hook does not alter the signed portal payload or Velocity admission:
it merely waits up to ten seconds for source pet preparation, then continues the existing route.
Pet Companion claims only a persisted reservation whose final backend exactly equals its configured
`authority.backend-id`; this excludes the intermediate waiting lobby. The service scans expired
five-minute reservations immediately and every five seconds, returning them to `HELD` after proxy
restart, destination outage, or player disconnect. Do not disable that scheduler while transfers
are enabled.

Velocity uses authenticated `POST /v1/presence` through the separate
`pet-velocity/build/libs/pet-velocity-*.jar`. Copy that JAR to Velocity's `plugins/` directory,
set the environment shown in `config/pet-velocity.env.example`, and restart the proxy. The plugin
publishes timestamped `PostLoginEvent` and true `DisconnectEvent` only; it deliberately does not
subscribe to backend connection/switch events or modify the existing admission/portal plugin.
Allow the private service port from the proxy host before enabling this integration.

The service also scans indexed `pet_sleep_state` deadlines immediately at startup and every
30 seconds in batches of at most 100. Sleep transitions use row locks and persisted UTC times;
do not delete or manually rewrite those rows during ordinary restart/rollback. Velocity-backed
network presence ingestion remains required before logout-triggered sleep is end-to-end.

Vector readiness remains degraded until discovery selects a provider and its adapter/model client
is installed. Do not treat the provider-neutral memory code as authorization to expose or deploy a
vector service. Follow `docs/VECTOR_OPERATIONS.md` after that decision.

```sh
curl --fail --header "Authorization: Bearer ${PET_SERVICE_TOKEN}" \
  http://127.0.0.1:8787/health/ready
```

Stage on one backend first. For application rollback, stop the unit, restore the prior `/opt/pet-companion` distribution, and restart it. Do not drop the additive pet tables. Database rollback/restoration must follow the verified backup procedure described in `db/README.md`.

Before any production write, install the backup service/timer and complete the disposable restore
drill in `docs/DATABASE_BACKUP_RESTORE.md`. A successful timer run is not enough: verify the
checksum, copy it to encrypted off-host storage, restore it into an isolated database, run the
structural verifier, and record measured RPO/RTO. The repository intentionally does not delete old
backups because retention must be reconciled with the host's existing backup policy.

## Component rollback boundaries

- **Pet service:** stop the unit, restore the previous versioned distribution under
  `/opt/pet-companion`, then start it and require both authenticated health checks to pass.
  If only AI is unhealthy, set `PET_AI_ENABLED=false` and restart; physical pet routes remain
  available while the provider problem is investigated.
- **Fabric backends:** drain and stop one backend, restore its previous mod JAR and matching
  configuration, restart it, then verify authority reconciliation before rolling back another
  backend. Restore the paired ServerPortals build at the same time; if only the optional hook is
  absent, portal routing still works but automatic pet carry is not guaranteed. Do not run mixed
  protocol versions unless their wire compatibility was verified.
- **MariaDB schema:** migrations are additive and the application rollback must leave pet tables
  intact. Never reverse a migration with ad-hoc `DROP` or destructive SQL; restore through the
  operator's verified database-backup procedure when a schema rollback is genuinely required.
- **Vector provider:** relational long-term-memory rows remain authoritative. Disable the adapter,
  retain SQL rows and pending jobs, restore the prior adapter/model configuration, then follow
  `docs/VECTOR_OPERATIONS.md` to reindex. Do not delete relational memories during rollback.
