# View Extend

Streams visual snapshots of already generated terrain outside each player's vanilla tracking area, up to 127 chunks total. Saved chunks are read without tickets, generation, block-entity loading, or distant ticking. Loaded vanilla chunks take precedence over saved data. Extended entity tracking supports players and live dragons tagged `sky_islands_managed_dragon`. Sky-Island owns dragon simulation and chunk tickets; View-Extend only extends observer tracking after terrain arrives. Other entities keep vanilla tracking.

Clients must support and request the desired render distance. Ungenerated chunks cannot be displayed. Extended terrain is a snapshot; normal updates resume inside vanilla tracking range.

## Architecture

- `ViewExtendService`: equal player budgets, shared requests, delivery, and metrics.
- `PlayerViewPlanner` / `PlayerViewState`: incremental coverage, retries, unloads, and session/request identity.
- `VisualChunkLoader`: bounded workers and a 30-second timeout covering reads and preparation without timing out Minecraft's original read future.
- `VisualChunkPreparer`: detached NBT, optional LOD, and lighting.
- `VisualChunkPackets`: worker-built packets shared across recipients, using the Minecraft 26.2 codec without constructing detached world chunks.
- `VisualChunkCache`: shared cache bounded by bytes, entries, and TTL.

Fabric lifecycle events own startup/shutdown and run delivery after vanilla updates client chunk-cache centers. Mixins handle radius/unloads, record vanilla chunk packets, and extend player tracking.

## Configuration

Version 3 backs up older configuration to `viewextend.properties.previous.bak`. For v2, only known old default tuning values are migrated; customized values and distances are preserved. Keep `config-version=3` when customizing settings.

| Setting | Default |
| --- | ---: |
| Maximum client distance | 127 |
| Request/send ceiling per player per tick | 64 |
| Global visual sends / ready processing per tick | 512 |
| Global disk-read starts per tick | 256 |
| Pending requests per player | 128 |
| Shared outstanding read/delivery capacity | 1024 |
| Shared packet-cache budget | 256 MiB |
| Cache entries / TTL | 65536 / 1200 ticks |
| Worker threads | Half the processors, bounded to 2-16; retire after 30 seconds idle |
| LOD start | 128; no section stripping at supported distances |

These global bounds target shared service for 20 players at distance 32; they are not a measured capacity guarantee. Player order rotates to share saturated capacity. Visual batches consume vanilla's remaining quota and outstanding-batch slots. Vanilla handles every acknowledgement, so nearby chunks get priority and extended delivery adapts to client processing speed, up to vanilla's 64 chunks/tick ceiling. TCP writability also gates delivery. Radius remains independently selectable up to 127. A slow subscriber does not block other recipients of the same prepared packet.

`max-main-thread-prepared-chunks-per-tick` also caps all visual sends, including cache hits and loaded chunks. Packet preparation stays on workers. Small coordinate queues are independent of the smaller payload lookahead. The cache uses estimated retained bytes, not wire bytes: uniform light arrays are shared globally, while network metrics continue counting their full transmitted size. Expiry metadata never owns packet payloads. Cache/retry memory is released when the server is empty; Java garbage collection determines when heap pages are reclaimed. In-flight work and networking have additional memory costs beyond the cache budget.

Missing chunks initially retry after 1200 ticks, unfinished chunks after 100. Repeated failures back off up to 6000 ticks, shared across players and LODs. A generation-stage change resets unfinished backoff; a chunk-load event invalidates cooldowns immediately. Movement prunes departed retries. Terrain written externally without a chunk-load event is discovered on the next retry. Retries cannot monopolize the initial scan; movement does not restart it. Returning during unload grace forces replacement data, and stale completions cannot clear newer requests or respawn sessions.

## Validation

Use Java 25:

```powershell
.\gradlew.bat build
.\gradlew.bat runMixinSmokeTest
```

Regressions cover coverage at 127, movement, rounded boundaries, unload grace, teleports, stale requests/sessions, retry cancellation, subscriber fairness, config migration, cache expiry/limits, worker outcomes, and Minecraft 26.2 packet/section/light round trips.

The smoke task force-transforms all five server target classes under Fabric, checks mixin interfaces and shared vanilla/visual ACK accounting, and runs settings-only startup under `build/mixin-smoke`. It does not start a world or accept an EULA. Test classes/resources are excluded from the release jar.

These checks do not measure real multiplayer throughput or verify connected-client rendering. A radius of 127 covers 65,025 square chunk positions; storage, compression, bandwidth, client memory, and rendering determine fill time. Live validation should include several players moving, standing, teleporting, changing dimensions, and reconnecting while observing the interval metrics.

Implementation was checked against local Minecraft 26.2 sources and Fabric API 0.160.0+26.2. Packet layouts and mixin contracts are version-specific.

## Flight diagnostics

`[ViewExtend Preparation]` reports failure counts by reason and shared cooldown hits. Expected unfinished/missing terrain produces an INFO example; actual failures produce a WARN with the root exception, world, position, and LOD. Examples are limited to one per reason per 1200 server ticks, independently of the metrics switch. Unfinished chunks are rejected before section decoding. Cooldowns are shared across players and LODs, bounded to 65536 source positions, and invalidated when a chunk loads. Pending retries for that position are then requeued immediately.

`[ViewExtend View]` reports each player's requested/effective/server distances, sent/pending/retry counts, bootstrap progress, remaining batch quota, and outstanding batches. `preparedDropped` still includes unfinished chunks for comparison with old logs; use the reason counts to distinguish them from errors. `mainElapsedMsps` and `workerElapsedMsps` replace the misleading CPU labels; ordinary failed preparations now contribute to worker timing. Timed-out work still running on a worker is not included in that timing. Network figures remain pre-compression estimates.

## Adaptive scheduling

Client ACK rate and smoothed preparation latency set each player's lookahead (4 to the configured pending ceiling). Scheduling uses a 0.1–4 ms soft time slice from the current server tick's remaining 50 ms budget. Read starts ramp gradually under demand and back off with tick or worker pressure, never exceeding the configured ceiling. A single operation or bookkeeping can overrun the soft slice; this is not a hard real-time guarantee.

Observers receive byte and elapsed-time deficit credits. Candidate work rotates one accepted request at a time, prepared deliveries get a fair pass, and unused shares can then be borrowed. Global packet, read, queue, cache and client-ACK limits still apply. Read dispatch runs before candidate selection so a saturated selection pass cannot starve IO.

Only one eighth of the packet cache is probationary (32 MiB at defaults), with a maximum five-second TTL. Reuse promotes entries into the protected remainder; requests shared across players go directly there. Cache hits do not continually extend protected expiry. This limits memory retained by one-way exploration without sacrificing coalescing. Scheduler metrics report read allowance, time budget, preparation latency and cache promotions; player metrics include current lookahead.
