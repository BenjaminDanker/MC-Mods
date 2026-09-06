# AI Pet System Implementation Map

Date: 2026-08-30

This note records local discovery for the binding handoff. Unknown deployment facts remain open; no assumption here weakens the handoff.

## Confirmed local baseline

- `Pet-Companion` began as an untracked directory containing only the handoff. Its Git root is the `MC-Mods` monorepo; there is no applicable `AGENTS.md`.
- Repository guidance targets Java 21, Minecraft 1.21.10, Yarn `1.21.10+build.1`, Fabric Loader 0.17.3, Fabric API `0.138.0+1.21.10`, Loom 1.11.8, and Gradle 8.14.
- `Util-Mods/Villager-Interface` is the existing private-chat input implementation. It captures signed Minecraft chat input during a session and suppresses its public broadcast; it has no dedicated client screen. Per the owner's clarification, this private transport is the intended pet input UI. Pet conversation reuses that capture/suppression shape but not the villager's automatic right-click model greeting, because pets may call AI only after valid submission.
- `Util-Mods/MC-Portal-Protocol` owns the canonical Fabric `wakeuplobby:portal_request` payload. Pet code must not register that payload again. Any later pet payload belongs in that shared protocol project after the exact transport is designed.
- `Velocity-Wakeup-Lobby` contains `StickyRouter`, `StickySessionManager`, `PortalCommandHandler`, admission/offline-wait logic, and regression tests. Its build currently compiles against Velocity API `3.2.0-SNAPSHOT`/Java 17 while its README says Velocity 3.4+/Java 21; deployed versions must be confirmed before pet transfer edits.
- `MPDS` contains the current JDBC/MySQL player persistence integration and reads connection values from its existing config mechanism. No migration framework was found locally. Secret values were not inspected or copied.
- No website/Stripe repository, MariaDB backup/schema administration files, systemd deployment definitions, or staging topology were found in the supplied workspaces.

## Initial module map

- `pet-common`: provider-neutral IDs, DTOs, validation, authoritative placement model, and transition rules.
- `pet-service`: repository contracts, transactional orchestration, JDBC/MariaDB adapter, sleep/memory/billing jobs, and internal API in later slices.
- `pet-fabric`: physical cat/wolf representation, placement/pickup/reconciliation, commands/compass, and conversation/display integration.
- Presence remains an isolated Velocity plugin. Automatic carry integrates before the existing signed `MCServerPortals` request and leaves `Velocity-Wakeup-Lobby` routing, one-shot markers, and admission bypass unchanged; a second competing router plugin is not justified.
- New portal payloads, if needed, should be added to `MC-Portal-Protocol`, not duplicated in `pet-fabric`.
- Website changes cannot be mapped until the actual website repository is available.
- Authoritative migrations live under `db/migrations`; the service remains isolated from vector-vendor APIs behind interfaces.

## Implementation order

1. Pure, testable authority model plus MariaDB migrations and repository contracts.
2. JDBC transaction/CAS adapter and race tests.
3. Fabric entity identity, exact appearance, safe placement/pickup, and reconciliation.
4. Commands/compass, then existing Velocity transfer integration.
5. Conversation display and bounded AI/memory only after physical authority is reliable.

## Known deviations and unresolved deployment facts

- The current automatic-carry hook is an optional, bounded reflection bridge from `MCServerPortals` to Pet Companion because no new wire payload is needed: the source already knows the final backend and the destination reads the persisted reservation. The existing signed portal payload bytes remain canonical and unchanged. This paired build must be staged together; direct proxy/backend-switch commands outside ServerPortals remain inventoried but are not yet carry-enabled or disabled.
- The pet flow now adapts Villager-Interface's private chat-bar capture/broadcast suppression while keeping its own short-lived owner/pet session and submit-only model trigger. A reflection-only compatibility guard prevents a pet session from opening over an active villager session; deployed two-mod verification remains required.
- Loader versions differ (`0.17.3` in current MC-Mods guidance versus `0.17.2` in MCServerPortals), and the Velocity build/README disagree on Java/API versions. Do not alter deployed integration targets until runtime versions are confirmed.
- The Raspberry Pi MariaDB version, `minecraft` schema DDL, credentials delivery, connection limits, backup process, and secure reachability from a service host require deployment-side inspection.
- Pet-service host, vector provider, website/Stripe integration point, staging server, expected load, acceptable tick/write/pool thresholds, final quota, and subscription price remain undecided.
- Target-version runtime verification is still required for scale hitboxes/pathfinding, cat/wolf registry variants, Text Display yaw, compass presentation, and multiplayer visibility.
