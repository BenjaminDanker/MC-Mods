# MariaDB backup and restore runbook

This runbook provides repository-side tooling; it does not prove that the Raspberry Pi's existing
backup job includes the selected database. Confirm the actual schema, MariaDB version, and local
storage before production writes. This runbook intentionally does not require encrypted off-host
replication, alerting, retention policy, or RPO/RTO measurement for the current deployment.

## Install and schedule

Install the four scripts from `deploy/scripts/` under `/opt/pet-companion/ops/` with owner
`root:root` and mode `0755`. Create a non-login `pet-backup` user and the output directory:

```sh
sudo install -d -o pet-backup -g pet-backup -m 0700 /var/backups/pet-companion
sudo install -d -o root -g pet-backup -m 0750 /etc/pet-companion
sudo install -o root -g pet-backup -m 0640 \
  config/backup-client.cnf.example /etc/pet-companion/backup-client.cnf
sudo install -o root -g pet-backup -m 0640 \
  config/pet-backup.env.example /etc/pet-companion/pet-backup.env
```

Fill the client file outside Git with a dedicated account that can consistently read the selected
database plus its triggers/events/routines. If MariaDB is remote, require certificate-verified TLS
in that file. Confirm the database name; do not assume `minecraft` merely because the example uses
it.

Install `pet-companion-backup.service` and `.timer`, run `systemd-analyze verify` and
`systemctl daemon-reload`, then execute one manual unit before enabling the timer. The script uses
`mariadb-dump --single-transaction --quick` for the all-InnoDB schema, streams into a mode-0600 gzip
file, refuses overwrite, and atomically publishes a SHA-256 sidecar. It deliberately performs no
automatic deletion; retain local artifacts according to the host's available disk policy.

```sh
sudo systemctl start pet-companion-backup.service
sudo journalctl -u pet-companion-backup.service --since today
sudo -u pet-backup /opt/pet-companion/ops/verify_pet_backup.sh \
  /var/backups/pet-companion/pet-companion-CONFIRMED_DB-TIMESTAMP.sql.gz
sudo systemctl enable --now pet-companion-backup.timer
```

The current deployment stops at the verified local artifact and restore drill. Off-host copies and
monitoring can be added later if the operational scope changes.

## Restore drill

Never make the first restore attempt against production. Provision an empty disposable database,
use a separate restore credential, and run the guarded command with the literal confirmation flag:

```sh
sudo -u pet-backup /opt/pet-companion/ops/restore_pet_database.sh \
  /var/backups/pet-companion/pet-companion-CONFIRMED_DB-TIMESTAMP.sql.gz \
  /etc/pet-companion/restore-client.cnf pet_restore_drill --confirm-restore
```

The restore refuses missing/malformed checksums, corrupt gzip streams, unsafe credential-file
permissions, invalid database names, and missing confirmation. After import it requires all 16 V001
InnoDB tables, all three V002 recall replay columns, both V003 Checkout-link columns, and complete
pet/trait/mood/sleep aggregates.
Then point an isolated pet-service instance at the restored database and verify authenticated
readiness, representative owner lookup, subscription access, held/placed state, sleep deadlines,
recall history, webhook ledger, memories, jobs, and idempotency replays without connecting any live
Minecraft backend.

Record backup timestamp, restore start/end, artifact hash, row-count comparison, and discovered
issues. Drop only the explicitly identified disposable drill database afterward using
the operator's normal database process. A production restore requires an approved outage, a fresh
pre-restore snapshot, exact target confirmation, stopped writers, and a reviewed decision about
data newer than the selected backup.
