# Sky-Islands

Fabric mod for the Sky-Islands server targeting Minecraft 26.2 with Fabric
Loader 0.19.3 and Java 25. The mod keeps the dragon, giant, portal,
night-ghast, special-item, and natural creeper-spawn features. Legacy
extended-distance entity-tracker and mob-spawner mixins are left out of the
metadata pending a separate internal-API rework.

## Build

```powershell
# From this folder
java -version
.\gradlew.bat jar
```

Place the resulting jar from `build\libs` into a 26.2 server `mods` folder.

## Managed dragon encounters

Managed dragons alternate between committed swoops, three-shot breath passes and close-range wing gusts. Each attack requires a 0.6-second approach confirmation, a one-second windup (0.7 seconds below half health), and a recovery window. Combat uses no action-bar or chat instructions and no projected attack-path markers. Swoops commit at release after one final capped aim refresh, so a jumping or strafing player does not invalidate a two-second-old target. Breath passes alternate crossing sides for stationary archers and take a capped current-movement lead for each individual shot; fired projectiles still never home. Gusts affect a 24-block forward cone and require line of sight. Vanilla wing knockback and head/body proximity damage are disabled except during an active swoop; the explicit wing-gust attack applies its own bounded damage/knockback.

Dealing 8 actual health damage during windup can stagger the dragon into a 3.75-second recovery, at most once per three-completed-attack cycle. Sustained arrow volleys therefore cannot repeatedly cancel every attack. Incoming hit knockback is disabled for managed dragons, preserving their flight velocity while retaining damage and vanilla hurt feedback. Below half health, recovery shortens modestly; the existing disengagement at one-eighth health remains. Dead, disconnected, spectator, distant and dimension-changed targets are released. Creative players who attack the dragon remain valid provokers, while keeping normal Creative damage immunity. Provocation immediately starts pursuit. These behaviors apply only to Sky-Island managed dragons, not vanilla End fights.

Dragon heads repel passive dragons from a horizontal column using `headOrbitRadiusBlocks + headAvoidSpawnBufferBlocks`, plus a 12-block body/turn margin. Once a player provokes a dragon, it ignores heads: they cannot divert pursuit, exclude combat targets, cancel attacks or block wing gusts. Cached avoidance state is cleared during aggression and avoidance resumes when the dragon returns to passive roaming. Wakes also ignore head shelter while their source dragon remains provoked; passive or orphaned wakes respect it. Both standing and wall heads are discovered when chunks load, including pending block-entity positions. Placement, commands, replacement and explosions update the index. Passive head searches are cached for at most 10 ticks, invalidated when heads change, and never load chunks.

Managed flight computes control at the start of `EnderDragon.aiStep`, then calls `move(SELF, velocity)` once at vanilla's flight-target lookup and returns a null target to skip vanilla's competing steering/acceleration/damping branch. Vanilla's subsequent multipart placement, collision damage and wall destruction remain active, as do flight history and death processing. This ordering is based on the local Minecraft 26.2 source. Combat creates at most three simultaneously tracked fireballs per dragon, cleans them up on interruption, and expires tracked misses after 120 server ticks (also removing them when their owner unloads).

Validation: `gradlew.bat build runDragonSmokeTest --offline` runs avoidance/combat timing regressions and transforms the actual dragon, phase-manager and chunk mixins under Fabric. Smoke startup uses `--initSettings`, does not start a world, and excludes smoke classes from the release jar.

### Exhaustion openings

After three completed attacks, the dragon becomes exhausted for four seconds: it audibly slows to a 0.4-block/tick glide toward the opponent's altitude and head hits deal 50% extra damage. The old white head sparkles have been removed. Body hits retain their normal damage. Interrupted windups do not count toward exhaustion; stopping or restarting an encounter clears the temporary head bonus. Existing dragon-head shelter and low-health disengagement rules remain in effect.

### Adaptive flight and wing drafts

Combat approach uses committed local waypoints instead of a repeating orbit. Every 1.5 seconds the dragon chooses pursuit, a bank through the less occupied side, a climbing escape from pressure below, or a breakout when players occupy three or more surrounding quadrants. Ranged pressure produces pursuit and breath passes; it no longer triggers the former 65-block evasive bank. Flying targets (including Creative flight) receive capped movement lead based on `ServerPlayer.getKnownMovement()`, the accepted client displacement; ground targets receive a lower pass. Turn rate is limited to 7.4° per tick and vertical velocity can change by 0.12 blocks/tick each tick, giving the dragon enough authority to climb or descend into a pass without twitching at every player movement. This is local flight steering, not terrain pathfinding; vanilla terrain destruction remains active, while dragon-head avoidance applies only to passive roaming.

Recent attackers are remembered for 30 seconds, including projectile owners. Target selection considers proximity, recent hits and a preference for retaining the current opponent; it normally changes only during approach, at most every two seconds. If the original attacker leaves, another remembered attacker can keep the encounter active. Nearby players influence escape direction even when they are not the selected opponent. Recent ranged fire favors banking and breath sweeps, flying opponents favor swoops, and a close frontal group favors wing gusts. Attack repetition limits, locked windups and recovery windows still apply. Swords and axes contribute the same close-range pressure; the dragon does not read inventories or predict un-fired arrows.

Motion leaves world-space wing upwash and trailing downwash segments. Each lasts 30 seconds, fading over the last ten, even after the dragon moves away or unloads. A dragon must travel at least 12 blocks and wait at least one second between emissions; low speeds below approximately 0.52 blocks/tick create none. Each world keeps at most 256 segments (oldest evicted under unusually heavy load). Overlapping segments use the strongest local current instead of stacking forces. All fields clear on server shutdown.

The recurring white cloud trail is removed. Drafts are discrete attack results: a swoop lays spaced updraft columns along its committed path, and a wing gust creates one downdraft column over the target area. No draft is created by passive travel, approach, breath attacks, recovery or exhaustion. Sparse purple dragon-breath particles show each column within 96 blocks. Columns last 30 seconds, have a 14-block horizontal radius and 24-block vertical half-height, and accelerate players every two ticks. Velocity packets start with `ServerPlayer.getKnownMovement()` and modify only Y, preserving the client's accepted forward and sideways movement exactly. Updrafts can launch grounded players and cap ascent at 1.8 blocks/tick. Downdrafts cap descent at 1.0 blocks/tick, stop within three blocks of terrain or on ground, and reset fall distance while active. Drafts produce no direct damage, text prompts or spawned entities.

Encounter observations run every five ticks and retain at most 64 nearby threats/attacker records per dragon. Target score ties are independent of player-list order. Changing targets after a disconnect or dimension change cancels an obsolete windup/attack. Automated coverage includes sustained one/four/twenty-participant approach scenarios, twenty-player target selection, wake expiry, emission limits and overlapping-wake force limits. These are deterministic logic tests; Fabric startup verifies the actual mixin transformation, but neither test category simulates connected clients or proves multiplayer latency and rendering behavior.

### Defensive spherical gust

Four melee hits from one or several players within a rolling three-second window trigger an immediate 30-block spherical pressure burst. Same-player damage hooks in one server tick count once. After firing, that dragon has an eight-second gust cooldown; projectile damage does not contribute. Trigger accounting happens only when the dragon receives damage.

For every affected non-spectator player, the burst uses the full normalized 3D vector from dragon to player. It decomposes the player's accepted client movement into radial and tangential components, adds the configured impulse, enforces a configured minimum outward launch speed, caps the outward radial speed, and recombines it with the unchanged tangential component. Defaults are a 1.7-block/tick impulse, 0.20-block/tick outward floor and 0.35-block/tick cap. A player flying inward at 1.5 blocks/tick leaves at 0.20 blocks/tick instead of cancelling the burst. Minecraft 26.2 retains 99% of horizontal Elytra velocity each tick, so the floor represents roughly 20 blocks of unobstructed travel while the cap keeps stationary or already-outgoing players near 35 blocks rather than launching them at extreme speed. It applies equally above, below and beside the dragon and does not add an upward bias. A 42-particle Fibonacci sphere of outward-moving white cloud particles shows the pressure wave.

The three values are written to `config/sky-islands-enderdragons.json` as `sphericalGustImpulseStrength`, `sphericalGustMinimumOutwardSpeed`, and `sphericalGustMaximumRadialSpeed`. Existing configs gain the defaults when loaded. Values are normalized to safe ranges, and the minimum is never allowed above the cap.

Affected players receive 40 ticks of fall-damage protection. A later spherical gust refreshes the deadline. During that window only the exact `minecraft:fall` damage type is capped at 6 damage; ender-pearl, stalagmite and all non-fall damage remain unchanged. No timer is scanned per tick: the deadline is stored on the player and checked only when `hurtServer` processes damage.
