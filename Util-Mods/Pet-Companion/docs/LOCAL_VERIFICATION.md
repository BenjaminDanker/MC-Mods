# Local verification

The repository includes a disposable MariaDB runner for JDBC checks. It uses the official
MariaDB Windows ZIP under `.local/tools`, binds only to `127.0.0.1:33317`, and stores its data and
generated password under ignored `.local/` files. It does not touch the Raspberry Pi database.

After downloading and checksum-verifying the ZIP, initialize/start the instance:

```powershell
.\deploy\scripts\local-mariadb.ps1 Start
```

Apply the checked-in V001–V003 migrations to the disposable `aipets_test` database, then run:

```powershell
.\deploy\scripts\local-mariadb.ps1 Test
```

The acceptance test checks the 16-table schema, a placement-state constraint, concurrent JDBC
adoption convergence, and restart-style persisted sleep evaluation. Stop the instance when done:

```powershell
.\deploy\scripts\local-mariadb.ps1 Stop
```

The local runner is verification infrastructure only. Production migration, backup, firewall,
TLS, credentials, and MariaDB-host decisions remain deployment operations.
