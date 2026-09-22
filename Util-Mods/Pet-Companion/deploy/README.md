# Pet service deployment checkpoint

This directory is a template, not authorization to deploy. Confirm the service host, MariaDB database/schema, backup path, private routing, and firewall policy before installation. Do not expose port 8787 publicly.

## Deployment topology

This repository intentionally uses placeholders for deployment-specific hostnames, IP addresses,
Unix usernames, and filesystem paths. Set the authority service host, gameplay/proxy host, MariaDB
host, and private firewall source addresses in the operator environment; do not commit those values
unless they are deliberately public. Keep the authority service and MariaDB on a private network,
bind the service to loopback or one reviewed private interface, and allow gameplay traffic only from
known backend/proxy addresses.

The service may run as a per-user or system unit. Keep its distribution and environment outside the
repository (for example, `/opt/pet-companion` and `/etc/pet-companion/pet-service.env`), with the
environment file mode `0600` or stricter. Qdrant should remain loopback-only and API-key-protected;
MariaDB remains authoritative. The selected deployment intentionally keeps backups local; encrypted
off-host replication, retention, and alerting are separate operational decisions.

## Build and install

Build the application distribution without embedding secrets:

```powershell
.\gradlew.bat :pet-service:installDist
```

Copy the contents of `pet-service/build/install/pet-service/` to `/opt/pet-companion/`. Create a dedicated `pet-companion` system user with no interactive login. Copy `config/pet-service.env.example` to `/etc/pet-companion/pet-service.env`, set mode `0600`, ownership `pet-companion:pet-companion`, and supply secrets through the approved host mechanism.

Apply every migration in `db/migrations/` in version order, including V001 through V005, using the operator-controlled process in `db/README.md`. The service never changes schema on startup.

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
- `PET_DUMMY_SUBSCRIPTION_ENABLED`: development/staging-only single-owner Stripe substitute; defaults to `false`, rejects simultaneous Stripe enablement, and requires `PET_DUMMY_SUBSCRIPTION_OWNER_UUID`. Never enable it for a public/production service.
- `PET_DUMMY_SUBSCRIPTION_OWNER_UUID`: exact UUID allowed to adopt and use AI while the dummy substitute is enabled; no usernames or wildcard access are accepted.
- `PET_STRIPE_PRICE_ID`: exact recurring Stripe Price ID accepted for AI entitlement when billing is enabled.
- `PET_SUBSCRIPTION_GROSS_USD`, `PET_STRIPE_PAYMENT_PERCENT`, `PET_STRIPE_FIXED_FEE_USD`, `PET_STRIPE_BILLING_PERCENT`: editable pricing policy used to derive the net AI allowance (defaults produce `$1.628` from a `$2.00` subscription).
- `PET_STRIPE_SECRET_KEY`: environment-specific `sk_test_*`/`sk_live_*` secret used only for outbound server-side Checkout Session creation.
- `PET_STRIPE_WEBHOOK_SECRET`: environment-specific `whsec_*` endpoint secret. Never reuse a test secret in production.
- `PET_PUBLIC_BASE_URL`: HTTPS origin hosting the public `/checkout/*` proxy routes; paths, queries, fragments, and HTTP are rejected.
- `PET_ACCOUNT_LINK_PEPPER`: unique random 32–512 character HMAC key; keep it distinct from the service bearer token and Stripe secret. Rotation invalidates outstanding opaque links.
- `PET_STRIPE_PAYMENT_GRACE_DAYS`: failed-payment grace, 0–14 days; defaults to 3.
- `PET_ACCOUNT_LINK_TTL_MINUTES`: application checkout-link lifetime, 2–30 minutes; defaults to 15. A started adoption checkout receives its own 24-hour hard cap.
- `PET_DB_POOL_MAXIMUM`, `PET_DB_POOL_MINIMUM_IDLE`: bounded pool sizes, default 4/1 and hard maximum 16.
- `PET_DB_CONNECTION_TIMEOUT_MS`, `PET_DB_VALIDATION_TIMEOUT_MS`: bounded database waits.
- `PET_QDRANT_ENABLED`: opt-in Qdrant vector index; defaults to `false` and must remain disabled until the endpoint is staged.
- `PET_QDRANT_URL`, `PET_QDRANT_COLLECTION`, `PET_QDRANT_DIMENSION`: Qdrant origin, collection, and fixed embedding dimension.
- `PET_QDRANT_API_KEY`, `PET_QDRANT_TIMEOUT_MS`: optional Qdrant API key and bounded request timeout; the key is never logged.
- `PET_OPENAI_API_KEY`: required when the OpenAI embedding worker is enabled; never place it in properties, logs, or Qdrant payloads.
- `PET_OPENAI_BASE_URL`, `PET_EMBEDDING_MODEL`, `PET_EMBEDDING_DIMENSION`: bounded OpenAI endpoint/model/dimension settings; the default model is `text-embedding-3-small` at 1536 dimensions.
- `PET_CONSOLIDATION_ENABLED`: separately opt-in sleep consolidation model calls; defaults to `false` and requires `PET_OPENAI_API_KEY` when enabled.
- `PET_CONSOLIDATION_MODEL`: structured consolidation model name; defaults to `gpt-5.6-luna` and is independent of the dialogue model setting.
- `PET_DIALOGUE_ENABLED`: explicit paid-chat switch; set `true` only after staging approval. The selected defaults are `PET_DIALOGUE_MODEL=gpt-5.6-luna` with reasoning disabled and `PET_MODERATION_MODEL=omni-moderation-latest`. `PET_SUBSCRIPTION_GROSS_USD`, `PET_STRIPE_PAYMENT_PERCENT`, `PET_STRIPE_FIXED_FEE_USD`, and `PET_STRIPE_BILLING_PERCENT` derive the per-period AI allowance after Stripe fees; dialogue, consolidation, and embedding tokens all consume that allowance. `PET_DIALOGUE_DAILY_REPLY_CAP` remains only as a legacy compatibility ceiling.

On a deployed host, keep these values in the protected environment file selected by the operator
(mode `0600`, or stricter). Readiness reports configuration state only; it does not prove that
OpenAI or Qdrant is reachable. Restart the service after any provider change.

Install the service environment as `root:pet-companion` mode `0640` or stricter. See
`docs/SECRET_MANAGEMENT.md` for the complete secret inventory and coordinated rotation order.

Fabric backends use `config/pet-companion.properties` and name their token environment variable with `authority.token-environment`. Set that variable to the same internal token without writing the token into the properties file. Also set the environment variable named by `authority.compass-signing-environment` to one persistent, distinct 32+ character signing secret shared by every pet-enabled backend; rotating it invalidates existing compasses, which `/pet compass` can replace. Configure every backend's player-facing label in `authority.backend-friendly-names`.

Conversation startup is controlled by `conversation.mode`. Use `staging` only on a test backend: it installs the private-chat/session/Text Display path and returns a deterministic local reply without calling an AI provider. Use `service` on a backend only after the authenticated central dialogue endpoint is enabled; it sends bounded requests to the central service and preserves the same private-chat/Text Display path. `disabled` keeps the input path installed but reports an unavailable transport instead of silently consuming right-clicks.

## Health and rollback

All health routes require the bearer token. `/health/live` reports process liveness.
`/health/ready` reports database reachability and requires all 17 V001/V005 InnoDB tables plus
V002's recall replay, V003's Checkout-link columns, V004's dialogue context-accounting column,
and V005's pending-adoption columns. Stripe, vector, and model report
`CONFIGURED` only when their validated local configuration is enabled; these are configuration
states, not provider-reachability probes. `/health/metrics` exports Prometheus text with bounded names and no owner, pet,
conversation, authorization, or raw-text labels. Keep it on the same backend-only network
boundary; it is not a public endpoint.

Gameplay backends use authenticated `/v1/pets` authority reads/physical mutations (including monthly recall and recall-failure compensation) and `/v1/adoptions` subscription-gated adoption on the same private listener. These routes are not public APIs and must remain behind the backend-only firewall policy.

Fabric backends also post bounded, authenticated deltas to `/v1/metrics/events` for stale/duplicate
entity discards and automatic transfer failures. The reporter is fire-and-forget with a bounded
in-flight limit; a service outage drops diagnostics only and cannot block a Minecraft tick. Keep this
route on the same private backend-only firewall boundary.

Admin commands are permission-separated under `/pet admin`. Recovery atomically moves a non-held
pet to `HELD` without changing its persistent identity/appearance, then queues loaded entities for
normal authority reconciliation. Recall reset releases only the current UTC month marker and
retains its historical audit row. The supporting `/v1/admin/*` route is backend-only and must never
be added to the public reverse proxy.

To rebuild embedding jobs from every active relational memory card, run the operator-only command
from the service host. It pages the SQL source of truth, enqueues jobs idempotently, and prints only
bounded counts; it does not delete vector data or accept a database name from the caller:

```sh
export PET_SERVICE_BASE_URI=http://127.0.0.1:8787
read -r -s PET_SERVICE_TOKEN
export PET_SERVICE_TOKEN
PET_REINDEX_PAGE_SIZE=250 deploy/scripts/reindex_memory.sh
unset PET_SERVICE_TOKEN
```

The bearer token is supplied to `curl` through its configuration input rather than command-line
arguments. Keep this command on the private service host; the endpoint is authenticated and is not
part of the public reverse proxy.

To measure actual provider usage and reconcile the configured allowance, run the
read-only aggregate report from the database host:

```sh
deploy/scripts/usage_cost_report.sh /path/to/protected-mariadb-client.cnf minecraft 30
```

The report prints only the window, active-subscriber count, successful-call count, average input
and output tokens, total estimated dialogue cost, and per-active-subscriber cost projections. The
runtime budget additionally includes consolidation and embedding rows in `ai_usage`; it never
selects owner UUIDs, pet UUIDs, prompts, or replies. Stripe's exact net can be reconciled later from
balance transactions without changing this configuration formula.

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
The timers here are deliberately separate: the account-link TTL defaults to ten minutes, while
the five-minute Stripe signature tolerance only rejects a request whose `Stripe-Signature`
timestamp is too old (or too far in the future). The success redirect is an inert acknowledgement;
it can be reached before any billing webhook and never enables access. If Stripe accepts the
Checkout completion but a subscription/invoice webhook is delayed or missing, the owner remains
without AI access until a verified lifecycle event is applied. Stripe retries failed deliveries;
an operator can also resend the event from Stripe after confirming the endpoint, and a later valid
delivery still converges the same subscription. Never treat the browser redirect or a `200` for
`checkout.session.completed` alone as proof of payment.
The recommended player entry point is `/pet billing`: it performs one entitlement read and shows
only the next valid action (`/pet link` for checkout or `/pet portal` for management). The legacy
`/pet link` and `/pet portal` commands remain available and are guarded against duplicate checkout.
The pet service now creates hosted subscription-mode Checkout and attaches the server-resolved
UUID as trusted Session/subscription metadata; no website form, account, or manual code entry is
required. Before generating a link, Fabric reads the authenticated
`/v1/subscriptions/access/{minecraft-uuid}` projection; `/pet billing` renders one next action
from the returned lifecycle state, and stale Checkout tokens are rejected before a Stripe Session
is created. Fabric `/pet portal` resolves the connected player's UUID to the Stripe customer ID
previously bound by a verified webhook, then returns Stripe's short-lived hosted billing-management
URL; no customer ID or code is shown or entered. Configure the Customer Portal separately in each
Stripe test/live environment. The exact public `/checkout/return` page is inert; subscription
changes still take effect only through verified webhooks. Use
`deploy/nginx/pet-stripe-webhook.conf.example` only after replacing its hostname/certificates; it
suppresses token-path access logs and returns 404 for internal bearer routes.

Unsubscription is handled in Stripe Customer Portal. A normal cancellation produces a verified
`customer.subscription.updated` event with `cancel_at_period_end=true`; the service keeps AI access
through the already-paid `current_period_end`, and its authoritative access read expires access at
that time even if the final `customer.subscription.deleted` delivery is delayed. The deletion event
then records `CANCELED`; the pet row and physical-pet commands remain intact. An immediate Stripe
cancellation disables access as soon as its verified deletion event is applied. No separate removal
queue or player-entered cancellation code is required.

No separate website is required. Stripe hosts the payment form and billing portal; the only
public application surface is the narrow callback/Checkout bootstrap above. For a VPS topology,
use `deploy/vps/pet-stripe-caddyfile.example` on the public VPS and
`deploy/systemd/pet-stripe-vps-tunnel.service` on the private service host. The tunnel binds only
`127.0.0.1:18788` on the VPS and forwards to the service host's loopback proxy at `127.0.0.1:8788`;
port 8787 and all bearer-protected routes remain private. Caddy terminates HTTPS for the operator's
public domain, and `PET_PUBLIC_BASE_URL` must be that HTTPS origin. The interactive `stripe listen`
command is staging-only and is not needed in production.

The VPS edge requires one privileged host setup because ports 80/443 and certificate storage are
root-owned. Copy `pet-stripe-caddyfile.example` and `install-pet-stripe-edge.sh` to the VPS, then
run the installer with `sudo` and the path to the edited Caddyfile; it installs Caddy, validates the
supplied hostname, and enables the service. On the private service host, create a dedicated SSH key
at `~/.ssh/pet-companion-vps`, add its public key to the VPS with
`permitlisten="127.0.0.1:18788"` and no shell/agent/X11 forwarding, install the supplied user
unit, then enable it with `systemctl --user enable --now pet-stripe-vps-tunnel.service`. This is
a one-time service setup; no terminal needs to remain open.

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
publishes timestamped `PostLoginEvent` and true `DisconnectEvent` events and, at proxy startup,
reconciles the complete current online-player snapshot through `/v1/presence/reconcile`. That
startup snapshot prevents persisted online state from surviving a service/proxy restart when the
player is actually absent. It deliberately does not subscribe to backend connection/switch events
or modify the existing admission/portal plugin. Allow the private service port from the proxy host
before enabling this integration.

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
drill in `docs/DATABASE_BACKUP_RESTORE.md`. Verify the checksum, restore it into an isolated
database, and run the structural verifier. This deployment deliberately uses local-only backup
retention; off-host encryption, alerting, and RPO/RTO measurement are optional future operations,
not release gates.

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
