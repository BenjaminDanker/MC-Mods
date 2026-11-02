# Pet Companion repository guide

Last verified: 2026-09-30. This file includes the deployment topology because agents need it while working in this repository. Host status, paths, units, and hashes can change; check the relevant facts before a rollout. Do not read or print `.env` files, credentials, process environments, or private player data. Never commit credentials, live player data, or generated build artifacts.

## 1. Project layout

| Path | Responsibility |
| --- | --- |
| `pet-common/src/main/java` | Shared domain rules, transport and wire types. `src/test/java` contains JUnit tests. |
| `pet-service/src/main/java` | HTTP service, MariaDB persistence, dialogue, memory, and Qdrant integration. Tests and fixtures live in `src/test/java` and `src/test/resources`. |
| `pet-fabric/src/main/java`, `src/main/resources` | Server-side Fabric gameplay integration, metadata, and mixins. `src/test/java` contains unit tests; `src/testmod/java` and `src/testmod/resources` contain in-game GameTests. |
| `pet-velocity/src/main/java` | Separate Velocity presence plugin. Tests live in `src/test/java`. |
| `db/migrations`, `db/README.md` | Ordered MariaDB migrations and schema procedure. MariaDB is authoritative for persistent pet state. |
| `deploy/`, `docs/` | Installation, verification and database procedures; operational and Dev Player documentation. |
| `config/` | Safe `.example` configuration templates. Module `src/main/resources` and `src/test/resources` contain packaged metadata and fixtures where present. |

The full build also needs sibling `../Authorization-Common` (included build) and `../Network-Authorization/fabric` (project dependency), as declared by `settings.gradle`. A `pet-common` change can affect the service distribution and the Fabric and Velocity outputs; rebuild and deploy the affected consumers.

## 2. Build and test

From the repository root on the Windows operator workstation, use Java 25 and the checked-in Gradle wrapper. The draft inventory reported Gradle 9.5.1; the wrapper is authoritative.

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat :pet-service:installDist
.\gradlew.bat :pet-fabric:runGametest
```

The runnable service distribution is `pet-service/build/install/pet-service`. The four commands were reported successful on 2026-09-30, including 29 required GameTests; that is historical evidence, so rerun checks after changes. Run focused tests while iterating, then `test` and relevant GameTests before a deployment. Verify database migrations against isolated data. Follow `docs/DEV_PLAYER.md` for model, SQL, Qdrant, and conversation checks.

Use UTF-8 Java, four-space indentation, existing `com.silver.aipets...` packages, `UpperCamelCase` types, `lowerCamelCase` members, and `UPPER_SNAKE_CASE` constants. JUnit 5 test classes end in `Test`; test names describe behavior. No repository-wide formatter or linter was reported.

## 3. Hosts and service scopes

Use the configured SSH aliases `ssh primary-ram`, `ssh raspberrypi`, and `ssh vps`. They were reported to log in as `silver`. Run screen and Dev Player commands as `silver`. `User=silver` in a system unit does **not** turn it into a user unit: inspect the unit path and use the correct systemctl scope.

| Host | Role | Service management |
| --- | --- | --- |
| `primary-ram` | Six Fabric backends and their `mods/` jars. No central Pet Companion service. | System units named `<session>.service`, each launching a detached GNU screen. Inspect with `systemctl`; console access with `screen -r <session>`. |
| `raspberrypi` | Central Pet Companion service; MariaDB and Qdrant; Velocity with the Pet Companion plugin; waiting lobby. | `pet-companion.service` is a **user** unit: `systemctl --user`. `velocity.service`, `mariadb.service`, `qdrant.service`, and `mc_waiting_lobby.service` are **system** units: `systemctl`. |
| `vps` | Public Caddy/Stripe edge. | `caddy.service` is a system unit. No Pet Companion jar, Velocity service, or Minecraft backend was reported here. |

Read-only checks from local PowerShell:

```powershell
ssh primary-ram 'id -un; screen -ls; systemctl is-active cave-mc-server desert-mc-server magic-mc-server ocean-mc-server sky-island-mc-server vanilla-mc-server'
ssh raspberrypi 'id -un; systemctl --user is-active pet-companion.service; systemctl is-active velocity.service mariadb.service qdrant.service'
ssh vps 'id -un; systemctl is-active caddy.service'
```

The `primary-ram` units were reported as `Restart=always`, `RestartSec=5`; their detached screen sessions are named after their units. Check the current units before relying on that policy. The six backends are:

| Server | System unit / screen session | Directory on `primary-ram` | Port | Jar inside that directory |
| --- | --- | --- | ---: | --- |
| Cave | `cave-mc-server` | `/mnt/mc-storage/cave-mc-server` | 25572 | `mods/pet-companion-0.1.0-SNAPSHOT.jar` |
| Desert | `desert-mc-server` | `/mnt/mc-fast/desert-mc-server` | 25571 | `mods/pet-companion-0.1.0-SNAPSHOT.jar` |
| Magic | `magic-mc-server` | `/home/silver/mc-servers/magic` | 25573 | `mods/pet-companion-0.1.0-SNAPSHOT.jar` |
| Ocean | `ocean-mc-server` | `/mnt/mc-storage/ocean-mc-server` | 25569 | `mods/pet-companion-0.1.0-SNAPSHOT.jar` |
| Sky Island | `sky-island-mc-server` | `/mnt/mc-fast/sky-island-mc-server` | 25568 | `mods/pet-companion-0.1.0-SNAPSHOT.jar` |
| Vanilla | `vanilla-mc-server` | `/home/silver/mc-servers/vanilla` | 25567 | `mods/pet-companion-0.1.0-SNAPSHOT.jar` |

On `raspberrypi`, the user unit `pet-companion.service` runs from `/home/silver/pet-companion` and was reported as `Restart=on-failure`, with a five-second delay. Its service jar is `/home/silver/pet-companion/lib/pet-service-0.1.0-SNAPSHOT.jar`; the runtime `pet-common` jar is adjacent under `lib/`. The system unit `velocity.service` runs from `/home/silver/mc-proxy/velocity`, with plugin `/home/silver/mc-proxy/velocity/plugins/pet-velocity-0.1.0-SNAPSHOT.jar`, and was reported as `Restart=always` with a five-second delay. The waiting lobby runs from `/home/silver/mc-proxy/lobby`; no Pet Companion jar was found there. The production MariaDB schema is `minecraft`.

## 4. Dev Player isolation

Use the isolated MariaDB schema `pet_companion_dev_player` and Qdrant collection `pet_companion_dev_player_memories`. Never point tests at production player rows or the production Qdrant collection. Read `docs/DEV_PLAYER.md` before running scenarios.

On `raspberrypi` as `silver`:

```bash
bash /home/silver/pet-companion/dev-player/dev_player.sh inspect
```

The same script offers `run-scenario` to exercise the isolated resources and `reset` to clear them; use `reset` only when the task calls for clearing Dev Player data. The checked-in `deploy/scripts/deploy_dev_player_service.sh EXPECTED_SHA256` verifies the candidate, stages it beside the installed jar as `.next`, verifies the staged hash, atomically renames it, restarts the user unit, and checks the active unit and installed hash. It creates no rollback copies. Run it only after build/test and candidate verification; then use `verify_dev_player_deploy.sh` for readiness checks. Its candidate is `/home/silver/pet-companion/dev-player/pet-service-candidate.jar`.

## 5. Deploy only changed artifacts

Complete builds and relevant tests first. Confirm current destination filenames, unit scopes, active sessions, and source jar names. Compute the built jar's SHA-256 and compare it with the uploaded candidate before replacement. Stage next to the destination as `<jar>.next` and rename it over the destination on the **same filesystem**. A rename replaces the directory entry without writing into the old jar. Do not copy bytes directly over a jar used by a running JVM. Do not create routine rollback copies or restore an earlier artifact automatically on a failed check. Report the failure and inspect its cause. Restart only affected processes.

### Service on `raspberrypi`

1. Run `:pet-service:installDist`. Check the distribution's `lib/` directory for the exact service jar and any changed runtime dependency jars. Apply schema changes through `db/README.md` before bringing up code that needs them; use isolated Dev Player resources to check migrations.
2. From local PowerShell, upload the changed jar to its exact destination with a `.next` suffix. For the reported service filename:

   ```powershell
   scp .\pet-service\build\install\pet-service\lib\pet-service-0.1.0-SNAPSHOT.jar raspberrypi:/home/silver/pet-companion/lib/pet-service-0.1.0-SNAPSHOT.jar.next
   Get-FileHash .\pet-service\build\install\pet-service\lib\pet-service-0.1.0-SNAPSHOT.jar -Algorithm SHA256
   ssh raspberrypi 'sha256sum /home/silver/pet-companion/lib/pet-service-0.1.0-SNAPSHOT.jar.next'
   ```

3. When the hashes match, rename the candidate on `raspberrypi` as `silver`, then restart the **user** unit. Stage and rename a changed adjacent `pet-common` jar in the same way before restarting once.

   ```bash
   mv -f /home/silver/pet-companion/lib/pet-service-0.1.0-SNAPSHOT.jar.next /home/silver/pet-companion/lib/pet-service-0.1.0-SNAPSHOT.jar
   systemctl --user restart pet-companion.service
   systemctl --user is-active pet-companion.service
   bash /home/silver/pet-companion/dev-player/verify_dev_player_deploy.sh
   ```

4. Require `/health/ready` to report `READY`, database `UP`, and migrations `CURRENT`. Inspect recent `journalctl --user -u pet-companion.service` startup and error lines if anything fails. A service-only jar change does not require Minecraft or Velocity restarts.

### Fabric backends on `primary-ram`

1. Build the deployable Fabric jar and identify it in `pet-fabric/build/libs/`; check its actual filename and hash. Upload it as `mods/pet-companion-0.1.0-SNAPSHOT.jar.next` to each of the six directories in the inventory. Compare each remote `sha256sum` with the local built jar **before** replacing any destination. No six-server deployment helper was reported under `deploy/scripts`.
2. Rename each verified `.next` jar over its destination **before** typing `stop` in its screen. This completes the replacement before the automatic restart can start the next JVM. Example on `primary-ram` for Cave, after verifying its staged hash:

   ```bash
   mv -f /mnt/mc-storage/cave-mc-server/mods/pet-companion-0.1.0-SNAPSHOT.jar.next /mnt/mc-storage/cave-mc-server/mods/pet-companion-0.1.0-SNAPSHOT.jar
   screen -r cave-mc-server
   ```

   At the Minecraft console type `stop` and press Enter. Repeat for the other five sessions, using their paths in the table. Run `screen -ls` if an attach fails; do not guess a session name. If the currently installed jar already has the built hash, that server needs no replacement or restart.
3. The screen session should exit after the server saves, and the system unit should relaunch it. Check `systemctl is-active <session>.service`, `screen -ls`, and that server's **new** `logs/latest.log` startup for `Done (...)! For help, type "help"` and errors. Then perform the Minecraft status check below. The six previously observed startup times were 3.927–9.850 seconds plus the reported five-second restart delay. Allow roughly 30 seconds after the old session exits; if it does not start, inspect the unit and startup errors rather than repeatedly restarting it.

### Minecraft status from local PowerShell

In one local PowerShell window, open the SSH tunnel:

```powershell
ssh -N -L 35567:127.0.0.1:25567 -L 35568:127.0.0.1:25568 -L 35569:127.0.0.1:25569 -L 35571:127.0.0.1:25571 -L 35572:127.0.0.1:25572 -L 35573:127.0.0.1:25573 primary-ram
```

In a second local PowerShell window, from the repository root:

```powershell
python deploy/scripts/check_minecraft_status.py 35567 35568 35569 35571 35572 35573
```

Require `OK` for each expected port; close the tunnel with Ctrl+C. Status and startup checks do not prove floating text, sound, movement, or player experience. Report those separately unless a player actually checked them.

### Velocity on `raspberrypi`

Only a changed `pet-velocity` plugin requires replacing `/home/silver/mc-proxy/velocity/plugins/pet-velocity-0.1.0-SNAPSHOT.jar` and restarting `velocity.service`. Stage the built jar beside the destination, match hashes, rename on the same filesystem, then restart and check the **system** unit and recent journal. Determine whether the `silver` account has restart permission before relying on it; use the configured system-unit operator if necessary. A service-only or Fabric-only change does not restart Velocity. No Pet Companion artifacts go to `vps` or the Raspberry Pi waiting lobby.

## 6. Dated reference hashes

These are **observations from the source document**, not desired-version pins. Check them against current production when diagnosing a mismatch; compare new deployments against the newly built artifact's hash.

| Artifact as reported 2026-09-30 | SHA-256 |
| --- | --- |
| Fabric jar on all six `primary-ram` servers | `7ef3588b8af74a02d7b94a1df2300c0dbc099653aeb5d81a314c807c44815fb9` |
| Service jar on `raspberrypi` | `bdc137c4140d7f6685980b18e94ad4a1f91b652c1adf444abeaeaf12be03d9a0` |
| Adjacent `pet-common` jar on `raspberrypi` | `27953b055f146f382a03258464e7300074f2a89fff47c2cf282a0a4445433cad` |
| Velocity plugin on `raspberrypi` | `0e96d96c667ee12dc76d92673b557c047e0d31bc154f30f91631522be26aad2c` |
