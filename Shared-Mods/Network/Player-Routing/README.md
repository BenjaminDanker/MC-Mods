# Player-Routing

Velocity plugin `player-routing`: the routing core of the proxy. Depends on `network-authorization` and `on-demand-servers`.

- Config (servers, groups, holding server, return order, return specials): `plugins/player-routing/config.yml`.
  On first start, if it is missing and `plugins/wakeuplobby/config.yml` exists, that file is copied over once.
- Routing database (visits, preferred/last server, completed branches, portal transfers): MariaDB, credentials from
  `~/.config/wakeuplobby/database.env` (`WAKEUP_DB_JDBC_URL`, `WAKEUP_DB_USER`, `WAKEUP_DB_PASSWORD`; env vars override).
  Table names keep their `wakeup_` prefix; migrations in `src/main/resources/db/migration` run at startup.
- Pre-connect policy: exclusive branches, completion gates, no `/server` for players, downward-only tiers.
- Sticky waits: a player waits in the holding server and is moved when the target answers (`StickyRouter`).
- Commands: `/wakeuplobby reload`, `/server`, `/forceserver`, `/return`, `/msg`.
- Public API for other proxy plugins: `com.silver.routing.api.Routing` (lives only in this jar; declare
  `@Dependency(id = "player-routing")`). Used by `portals` and `waiting-lobby`.

The admission queue, offline waiters, fresh-join placement, kick redirect and waiting boss bar are in `Waiting-Lobby`.
`docs/` holds the deployment notes carried over from the old single plugin.

## Build

```bash
sh gradlew -q build   # build/libs/player-routing-1.0.0.jar
```
