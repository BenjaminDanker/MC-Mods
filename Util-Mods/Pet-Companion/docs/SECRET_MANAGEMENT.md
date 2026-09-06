# Secret management and rotation

No secret value belongs in Git, JVM arguments, Minecraft properties, command output, health JSON,
metrics labels, or public chat. The checked-in examples contain only empty values or names of
environment variables.

## Files and ownership

Install `/etc/pet-companion/pet-service.env` as `root:pet-companion` mode `0640` (or stricter) and
the MariaDB dump client file as `root:pet-backup` mode `0640` or `pet-backup:pet-backup` mode
`0600`. The systemd units run under distinct non-login users. Do not put a password directly in a
unit's `ExecStart`, where it can appear in process inspection.

The service environment currently owns:

- `PET_SERVICE_TOKEN` — private Fabric/Velocity authentication;
- `PET_STRIPE_SECRET_KEY` — outbound server-side Checkout Session creation only;
- `PET_DB_USER` and `PET_DB_PASSWORD` — least-privilege pet-service database account;
- `PET_STRIPE_WEBHOOK_SECRET` — environment-specific Stripe endpoint signing secret;
- `PET_ACCOUNT_LINK_PEPPER` — independent HMAC key for one-time opaque-link digests.

Every Fabric backend resolves the environment names configured by
`authority.token-environment` and `authority.compass-signing-environment`. Velocity reads its
service token and private origin from its process environment. Model/vector secrets must follow
the same pattern when their providers are selected; do not add placeholder live keys now.

Use separate development/test/production values. The service token, compass key, Stripe signing
secret, link pepper, database password, and future provider keys must all be distinct.

## Rotation order

1. Take and verify a database backup; record the maintenance ticket without copying secrets.
2. Rotate the database credential in MariaDB and `/etc/pet-companion/pet-service.env`, restart the
   service, then require authenticated readiness to pass.
3. Rotate `PET_SERVICE_TOKEN` during a coordinated service/Fabric/Velocity restart.
   Mixed values fail closed with HTTP 401; physical authority remains in SQL.
4. Rotate the compass key only deliberately: existing compass signatures become invalid and
   `/pet compass` must reissue them.
5. Rotate the outbound Stripe secret key in the Stripe dashboard and service environment, restart,
   and verify test-mode Checkout creation before revoking the prior key.
6. Add/roll the Stripe endpoint secret according to the provider's overlap procedure, deploy the
   new value, verify a signed test event, and remove the old secret. Never infer success from a
   browser redirect.
7. Rotating the account-link pepper intentionally invalidates all unconsumed short-lived links;
   players can request another with `/pet link`.

After each rotation, search service logs only for bounded failure categories. Never paste secret
values into diagnostic commands or tickets. If a secret is committed, revoke it first, then remove
it from current files and follow the repository owner's history-remediation process.
