# Atlantis Leviathan Roaming System — Specification

## 1) Goal

Implement a **roaming overworld leviathan system** in Atlantis that mirrors Sky-Islands’ dragon virtual-roam architecture:

- Leviathans exist primarily as **virtual tracked entities**.
- They are **materialized as real entities only near players**.
- When players leave range, real entities are despawned and virtual travel continues.
- Leviathans are giant fish with custom attack logic: **charge passes through players** until disengage/death conditions are met.

This document is the source-of-truth specification for implementation.

---

## 2) Baseline Architecture to Mirror (from Sky-Islands)

The leviathan system must follow the same core lifecycle pattern used by Sky-Islands dragon roaming:

1. **Global manager tick** on server tick.
2. Maintain a **persistent virtual state store** (id, position, heading, lastTick).
3. Ensure a configured **minimum world population** exists in virtual store.
4. For each virtual state:
	- advance virtual movement,
	- activate/materialize near players,
	- despawn when far from players,
	- keep virtual state synchronized.
5. Persist virtual state periodically and on shutdown.
6. Recover from crashes/unflushed shutdown by reconciling loaded managed entities back into virtual state.

### Required Atlantis components

- `LeviathanManager` (equivalent role to `EnderDragonManager`)
- `VirtualLeviathanStore` (equivalent role to `VirtualDragonStore`)
- `LeviathansConfig` JSON-backed config class
- `LeviathanIdTags` helper for entity command tags + stable internal UUID
- optional `LeviathanChunkPreloader` (same chunk-ticket concept; see section 8)
- mixin/class hook for entity runtime behavior (movement/attack overrides)

---

## 3) Entity Type and “Huge Fish” Rules

### 3.1 Base entity

Leviathan will be represented by a configurable vanilla fish/aquatic mob type.

- Config field `entityTypeId` (string identifier like `minecraft:salmon`).
- Must validate at load-time and **hard-fail** startup if invalid (no default fallback).

### 3.2 Size scaling

The fish must be made visually/physically huge, with scale controlled by config.

- Config field `entityScale` (double; > 0).
- Use Fabric-compatible approach for entity scaling in current target MC/Fabric version.
- If dynamic runtime scaling is not available for chosen entity/class, system must:
	- throw startup/config error,
	- stop initialization (no silent fallback path).

### 3.3 Managed tagging

All spawned leviathans must include:

- managed tag (e.g. `atlantis_managed_leviathan`)
- stable internal id tag (prefix + UUID)

This ID links loaded entities to virtual records and is mandatory for recovery.

---

## 4) Virtual State Model

Each virtual leviathan record must include:

- `id: UUID`
- `pos: Vec3d` (x,y,z)
- `headingX: double`
- `headingZ: double`
- `lastTick: long`

Heading must always normalize to unit length with no fallback.
If a heading becomes zero/invalid, treat as fatal state/config error.

Persist file in Atlantis config dir, e.g. `atlantis-leviathans-virtual.json`.

---

## 5) Core Lifecycle Requirements

### 5.1 Initialization

On Atlantis mod init:

- load/create `LeviathansConfig`
- instantiate `VirtualLeviathanStore`
- register tick event
- register shutdown flush

### 5.2 Ensure minimum count

Periodically (configurable cadence), if virtual count < configured minimum:

- create new virtual leviathans with random heading
- spawn positions sampled in configured roam ring around world spawn
- obey world border and configured no-spawn constraints

### 5.3 Activation (virtual → loaded)

If player is within `activationRadiusBlocks` of virtual position and spawn is ready:

- materialize entity at virtual position
- apply size scaling
- set managed tags + internal id tag
- initialize movement state and grace period
- outside of player's view distance

### 5.4 Deactivation (loaded → virtual)

If loaded leviathan has no player within `despawnRadiusBlocks`:

- update virtual state from current loaded position (preserving configured roam altitude policy)
- discard loaded entity
- release per-entity runtime tracking data
- release chunk tickets (if chunk preloading enabled)

### 5.5 Crash recovery

On periodic reconciliation:

- scan loaded managed leviathans
- if missing from virtual store, rebuild virtual record from live entity pos + heading
- avoid re-adding entities in death/invalid state

---

## 6) Roaming and Steering Requirements

Leviathan virtual movement must follow same concept as dragons:

- move by `virtualSpeedBlocksPerTick * dt` along heading
- loaded in fish entity speed should be config value, same as where virtualSpeedBlocksPerTick comes from and should be similar speed by default
- clamp to world border safe margin
- no jitter heading
- enforce roam band relative to world spawn:
  - if too close (`roamMinDistanceBlocks`): steer outward
  - if too far (`roamMaxDistanceBlocks`): steer inward
- steering must be smooth turn-limited (no heading snap)

### 6.1 Water-awareness

Because this is fish-based, add water constraints:

- configurable preferred depth/altitude band
- spawn/materialize only when location is water-valid
- move heading to go around/above/below solid blocks while remaining submerged
- obstacle avoidance is the primary recovery behavior
- the entire world from ground to sky ceiling is entirely water, so the fish if low enough, must go around or above mountains. Or, must go around/above/below player built obstacles

---

## 7) Combat/Attack Behavior (“Charge Passes”)

Fish do not naturally perform lethal passes, so custom logic is required.

### 7.1 Engagement trigger

A leviathan enters attack mode when a valid target player is within `engageRadiusBlocks` while loaded.

### 7.2 Attack loop

While engaged, leviathan performs repeated **charge passes**:

1. acquire/refresh target player
2. choose intercept vector (optionally lead target velocity)
3. accelerate into charge window for `chargeDurationTicks`
4. pass through/near player and continue to overshoot distance
5. perform turn-around/circle-back phase
6. repeat until disengage condition

### 7.3 Disengage conditions

End engagement if any of:

- target player dies
- leviathan dies
- target leaves `disengageRadiusBlocks`
- line-of-engagement invalid too long (configurable timeout)

### 7.4 Charge hit detection (advanced requirement)

Need robust detection that a player was truly hit by a charge, not merely nearby.

Required detector approach (all must be implemented together):

1. **Charge phase gate**: only detect hits during active charge window.
2. **Swept-volume test** between previous and current leviathan position each tick:
	- capsule/segment vs expanded player bounding box check.
3. **Directional gate**:
	- leviathan forward vector must be sufficiently aligned toward target (dot threshold).
4. **Speed gate**:
	- current speed ≥ `chargeMinHitSpeed`.
5. **Per-pass hit cooldown**:
	- prevent multiple hits in same pass from overlapping boxes.

On valid hit:

- apply configured `chargeDamage`
- reducuction of configured damage according to player's equipped armor and protection enchantment on equipped armor pieces
- optional knockback in charge direction
- mark pass as having connected for cooldown logic/telemetry

### 7.5 Safety constraints

- no damage outside active charge phase
- no repeated same-tick multi-hit exploit
- avoid invulnerable-state bypass (respect vanilla damage flow)

---

## 8) Chunk Loading and Spawn Readiness

Mirror dragon approach with budgeted ticketing, behind config flag.

If enabled:

- maintain per-leviathan desired chunk set:
  - radius around center + ahead-of-heading chunks
- request tickets with per-tick budget
- spawn only when center chunk is ready
- release stale tickets after timeout and on despawn

If disabled:

- only spawn when center chunk already loaded naturally

---

## 9) Persistence, Diagnostics, and Recovery

### 9.1 Persistence files

- `atlantis-leviathans.json` (config)
- `atlantis-leviathans-virtual.json` (virtual state)

### 9.2 Flush policy

- flush virtual state every `virtualStateFlushIntervalMinutes` (>0)
- flush on server stopping

### 9.3 Logging

Include info-level lifecycle events:

- virtual creation
- materialize spawn
- despawn to virtual
- recovery of missing virtual state

Debug-level for steering/engagement/hit-detection internals.

---

## 10) Commands / Debug Introspection

Add command(s) for parity with dragon observability:

- dump virtual + loaded leviathan summary
- include id short form, position, heading, engagement state, target

---

## 11) Configuration Schema (Required)

At minimum, config must expose these user-requested fields:

- `entityTypeId` (string) — which fish/aquatic mob to use
- `entityScale` (double) — giant size multiplier
- `minimumLeviathans` (int) — amount in world

Additional required/strongly recommended fields:

### Population and roaming
- `virtualTravelEnabled` (bool)
- `roamMinDistanceBlocks` (int)
- `roamMaxDistanceBlocks` (int)
- `spawnY` (int) / `spawnYRandomRange` (int)
- `virtualSpeedBlocksPerTick` (double)

### Player proximity lifecycle
- `activationRadiusBlocks` (int)
- `despawnRadiusBlocks` (int)
- `minSpawnDistanceBlocks` (int)
- `autoDistancesFromServer` (bool)

### Combat and charge
- `combatEnabled` (bool)
- `engageRadiusBlocks` (int)
- `disengageRadiusBlocks` (int)
- `chargeDurationTicks` (int)
- `chargeCooldownTicks` (int)
- `chargeSpeedBlocksPerTick` (double)
- `chargeMinHitSpeed` (double)
- `chargeDamage` (double)
- `chargeHitCooldownTicks` (int)
- `passOvershootBlocks` (double)
- `turnaroundTicks` (int)

### Water behavior
- `requireWaterForSpawn` (bool, default true)
- `solidAvoidanceProbeDistanceBlocks` (int)
- `solidAvoidanceVerticalClearanceBlocks` (int)
- `solidAvoidanceTurnStrength` (double)
- `minWaterDepthBlocksForTravel` (int)

### Chunk preloading
- `forceChunkLoadingEnabled` (bool)
- `preloadRadiusChunks` (int)
- `preloadAheadChunks` (int)
- `preloadTicketLevel` (int)
- `maxChunkLoadsPerTick` (int)
- `releaseTicketsAfterTicks` (int)

### Persistence
- `virtualStateFlushIntervalMinutes` (int)

All fields must be strictly validated (no silent coercion/fallback). 
Invalid values must hard-fail at startup with explicit key/value error details.
Command to load config file without server restart should be exposed to admins, including auto-complete.
Reload command behavior: reject invalid file, keep currently-active config, and return explicit validation errors to the command sender.

---

## 12) State Machine (Leviathan)

### Global materialization states

- `VIRTUAL_ONLY`
- `LOADED_PASSIVE`
- `LOADED_ENGAGED`

### Combat substates (inside `LOADED_ENGAGED`)

- `ACQUIRE`
- `CHARGE`
- `PASS_THROUGH`
- `TURN_BACK`
- `REACQUIRE`

Transitions:

- passive → engaged when target in engage range
- engaged → passive on disengage conditions
- loaded → virtual_only on despawn radius condition

---

## 13) Non-Functional Requirements

- Hard crash on startup for invalid config/entity type/scale API mismatch.
- No silent fallbacks anywhere in leviathan lifecycle/config parsing.
- For in-game config reload command, do not apply invalid config; report exact invalid keys/errors to command sender.
- O(n) per-leviathan tick acceptable, but avoid unbounded global scans where possible.
- Chunk ticket requests must be budgeted per tick.
- Use deterministic IDs and duplicate-protection to avoid double materialization.
- Keep server impact bounded with configurable caps.

---

## 14) Acceptance Criteria

1. System keeps at least `minimumLeviathans` virtual leviathans globally.
2. Leviathans roam virtually when no players nearby.
3. Leviathan materializes near players and despawns when players leave.
4. Config can switch leviathan base fish mob and scale size.
5. Engaged leviathan performs repeated charge passes.
6. Player damage occurs only on validated charge-hit detection.
7. Engagement stops correctly on death/range conditions. Stays in area for configurable time (default 5 minutes) if player disconnects, roaming in a circle around point in hopes of player reconnecting.
8. State persists across restart (virtual positions/headings remain).
9. No duplicate loaded leviathan for same internal ID.
10. Debug dump command reports coherent loaded + virtual state.

---

## 15) Implementation Checklist (Execution Order)

1. Create `LeviathansConfig` with strict schema validation + fail-fast startup loading + reload validation path.
2. Create `VirtualLeviathanStore` with persistence + de-dup + flush semantics.
3. Create ID/tag helper and managed predicate.
4. Create `LeviathanManager` tick loop (minimum count, virtual travel, materialize/despawn, recovery).
5. Add optional chunk preloader and spawn-readiness gating.
6. Add leviathan runtime behavior hooks (passive movement + combat engagement + charge passes).
7. Implement swept-volume charge-hit detector with cooldown/speed/direction gating.
8. Add commands/debug dumps.
9. Wire init into Atlantis bootstrap.
10. Validate with runtime tests and config permutations.

---

## 16) Out of Scope (for initial implementation)

- New custom model/renderer assets.
- Client-only VFX polish systems.
- Multi-target squad coordination between leviathans.
- Cross-dimension roaming.

---

## 17) Notes

- This spec intentionally mirrors Sky-Islands’ dragon virtual roam pattern first, then adapts fish-specific movement/combat semantics.
- If vanilla fish AI conflicts with deterministic pass behavior, manager/mixin logic takes authoritative control during managed loaded states.

