# AI pet database migrations

`migrations/V001__create_ai_pet_schema.sql` is the forward-only MariaDB baseline for the authoritative pet store. `V002__make_recall_operations_replayable.sql` adds the request fingerprint and exact stored response required by the recall workflow. `V003__bind_account_links_to_stripe_checkout.sql` binds each opened opaque link to the exact Stripe Checkout Session consumed by its webhook. `V004__persist_dialogue_context_usage.sql` adds bounded prompt-part accounting to provider usage rows without storing raw prompts. `V005__persist_pending_adoptions.sql` persists the exact species/name intent and its PRE_CHECKOUT/CHECKOUT_STARTED lifecycle. Apply all migration files in version order.

The migration is additive and contains no credentials, `USE` statement, startup hook, or vector-vendor dependency. The handoff identifies the host-native Raspberry Pi MariaDB as the selected SQL server and `minecraft` as its current database, but deployment inspection still must confirm whether these tables belong directly in `minecraft` or in a separate database on that same instance. Select the approved database explicitly when applying it.

## Compatibility choices

- UUIDs are lowercase hyphenated `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`, avoiding MariaDB-version-specific UUID types and byte-order ambiguity.
- Times are `DATETIME(6)` UTC. Every service connection must set its session time zone to `+00:00`; application clocks still supply authoritative instants.
- Structured payload/tag columns are `LONGTEXT`. The service must parse, validate, size-limit, and serialize JSON rather than relying on a version-specific MariaDB JSON type.
- Tables use InnoDB foreign keys, unique keys, state enums, and checks. Some old MariaDB releases accepted but did not enforce `CHECK`; boundary validation remains mandatory, and the live server version/enforcement behavior must be tested before deployment.
- `record_version` is incremented by transactional compare-and-set service updates. No trigger silently changes it.
- `pet_recall_usage` keeps one row per operation. `consumed_period_key` is null for `STARTED`/`FAILED` and equals `period_key` for `SUCCEEDED`; `UNIQUE (pet_id, consumed_period_key)` therefore allows failed attempts to remain auditable while permitting only one successful consumption per pet/month.

## Apply

1. On the actual host, record `SELECT VERSION(), DATABASE(), @@session.time_zone;`, inspect existing table names, confirm backup/restore procedures, and take a tested backup. Do not print or place credentials in this repository.
2. Apply first to a disposable/staging database using the existing operator credential mechanism. For example:

   ```sh
   mariadb --defaults-extra-file=/secure/path/client.cnf --database=confirmed_pet_database \
     < db/migrations/V001__create_ai_pet_schema.sql
   mariadb --defaults-extra-file=/secure/path/client.cnf --database=confirmed_pet_database \
     < db/migrations/V002__make_recall_operations_replayable.sql
   mariadb --defaults-extra-file=/secure/path/client.cnf --database=confirmed_pet_database \
     < db/migrations/V003__bind_account_links_to_stripe_checkout.sql
   mariadb --defaults-extra-file=/secure/path/client.cnf --database=confirmed_pet_database \
     < db/migrations/V004__persist_dialogue_context_usage.sql

   mariadb --defaults-extra-file=/secure/path/client.cnf --database=confirmed_pet_database \
     < db/migrations/V005__persist_pending_adoptions.sql
   ```

3. Apply each migration exactly once through the chosen migration/operator process. MariaDB DDL causes implicit commits, so a migration file is not an all-or-nothing transaction. Never run migrations automatically from pet-service startup.
4. Add the selected database and all new tables to the existing backup job before production
   writes begin. If no suitable job exists, install the checked-in backup service/timer and follow
   `docs/DATABASE_BACKUP_RESTORE.md`; do not treat an untested local dump as disaster recovery.

## Verify

In the selected database, the following should return 17 InnoDB tables:

```sql
SELECT table_name, engine
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'pets', 'pet_traits', 'pet_mood', 'pet_sleep_state', 'pet_events',
    'long_term_memories', 'long_term_memory_source_events',
    'long_term_memory_revisions', 'trait_change_audit', 'subscriptions',
    'stripe_webhook_events', 'pet_recall_usage', 'ai_usage', 'jobs',
    'account_link_tokens', 'idempotency_requests', 'pending_adoptions'
  )
ORDER BY table_name;
```

Then inspect enforcement and critical indexes rather than assuming the DDL was honored:

```sql
SHOW CREATE TABLE pets;
SHOW CREATE TABLE pet_recall_usage;
SHOW CREATE TABLE account_link_tokens;
SHOW CREATE TABLE pending_adoptions;
SHOW INDEX FROM pet_events;
SHOW INDEX FROM jobs;
SHOW INDEX FROM idempotency_requests;
```

In a rollback-only smoke transaction, insert a valid `HELD` pet and its trait/mood/sleep rows, verify that a duplicate `owner_uuid` is rejected, and verify that an invalid placement-state field combination is rejected. Separately exercise concurrent adoption, placement CAS, transfer claim, recall failure/success, webhook replay, and job claim transactions through the JDBC repository tests. Those live MariaDB syntax, constraint-enforcement, and transaction/race checks remain pending until the actual MariaDB version and test instance are available.

For recall, insert or retrieve the `STARTED` operation by `operation_id`. On a failed attempt, commit it as `FAILED` with no `consumed_period_key`. In the same database transaction that commits the authoritative successful placement, update it to `SUCCEEDED`, set `consumed_period_key = period_key`, and set `used_at`/`completed_at`; the unique key is the final concurrency guard. V002's fingerprint prevents key reuse for a different request and `response_json` provides exact replay. Spawn-failure compensation verifies the exact committed entity/revision, returns authority to `HELD`, and clears the consuming key in the same transaction.

## Rollback and recovery

There is intentionally no down migration. For an application rollback, leave these additive tables in place and deploy the prior application; this preserves pet identity and history. If staging application fails partway through, restore the pre-migration backup or discard/recreate only the confirmed disposable database before retrying—do not blindly rerun a partially applied baseline.

Production schema removal or incompatible correction requires a new, reviewed forward migration and a verified backup. Do not drop pet tables merely because a subscription ends or an application release is rolled back.

Repository tooling under `deploy/scripts/` creates consistent streamed logical backups, verifies
their SHA-256/gzip integrity, guards restore behind an explicit confirmation, and checks all V001
tables/V002/V003 columns plus required pet aggregate rows after restore. The selected deployment
uses this local backup/restore path; no separate encrypted/off-host policy is required for release.
