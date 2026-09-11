# Minecraft AI Pet System — Complete Implementation Handoff and Checklist

## 0. Purpose of this document

This document is the implementation contract for an IDE-capable coding AI. It captures the product decisions, network constraints, data model, AI/memory architecture, Fabric and Velocity behavior, Stripe subscription behavior, failure recovery, security requirements, observability, and acceptance tests for a persistent AI pet system spanning a Minecraft Velocity/Fabric network.

Treat statements labeled **MUST** as fixed requirements. Treat **SHOULD** as the preferred implementation unless existing code makes a materially simpler equivalent possible. Treat **CONFIGURABLE DEFAULT** as a value that should be implemented in configuration rather than hard-coded. Treat **DEFERRED** as explicitly out of the first release.

Do not start by rewriting existing server infrastructure. First inspect the repositories, existing portal/chat/website code, Minecraft/Fabric/Velocity versions, mappings, database configuration, and deployment scripts. Reuse working systems where practical.

---

## 1. Existing Minecraft network context

### 1.1 Network topology

Players enter through a Raspberry Pi 5 that runs Velocity and a minimal Fabric waiting lobby. The gameplay backends run on a separate Ubuntu machine named `primary-ram`.

```text
Internet
  -> Raspberry Pi
      -> Velocity proxy :25565
      -> Fabric waiting lobby :25566
      -> primary-ram backend machine
          -> vanilla1    :25567
          -> sky-island  :25568
          -> ocean       :25569
          -> desert      :25571
          -> cave        :25572
          -> magic       :25573
```

There is a separate Cultivation server on port `25590`. **Do not modify or integrate it unless explicitly requested.**

### 1.2 Existing infrastructure that must remain intact

- Velocity is the permanent public gateway.
- The waiting lobby is intentionally tiny and has a maximum of 100 players.
- Backend Minecraft ports are restricted by firewall to the Pi. Do not expose them broadly.
- The six backend JVMs use dynamic ZGC memory sharing.
- A host-level memory controller on `primary-ram:25580` controls admission of fresh external players.
- Internal backend-to-backend transfers bypass admission.
- Offline backend waiting and the normal admission queue are separate systems.
- Queue priority is `ADMIN > BIG_VIP > VIP > NORMAL`.
- Existing internal transfers use the waiting lobby plus `StickyRouter`, `StickySessionManager`, `PortalCommandHandler`, and one-shot internal markers.
- A shared Fabric portal protocol mod is the canonical Fabric owner of `wakeuplobby:portal_request`. Never create duplicate payload registrations for that channel.

### 1.3 Relevant server locations from the previous project

- Pi OS: Debian 12 on Raspberry Pi 5.
- Velocity directory: `/home/silver/velocity`.
- Waiting lobby directory: `/home/silver/mc_waiting_lobby`.
- Pi services: `velocity.service`, `mc_waiting_lobby.service`.
- Backend OS: Ubuntu Server 24.04.2 LTS on `primary-ram`.
- Java: normally Java 21.

### 1.4 Resource-isolation rule

The AI pet system **MUST NOT** destabilize the Minecraft JVMs or interfere with the admission controller.

- No model calls, database waits, HTTP waits, embedding operations, Stripe calls, or blocking file operations on the Minecraft server tick thread.
- Minecraft-thread entity mutations must be scheduled back onto the correct server thread after asynchronous work completes.
- The pet service should run as a separate process/service with explicit memory and restart limits.
- Do not start or enable Ollama on `primary-ram` as part of this feature.

---

## 2. Fixed product decisions

### 2.1 Product identity

This is a paid, persistent, cute companion—not a combat pet, generic chatbot, survival simulator, or pay-to-win feature.

The core value proposition is:

> A player's specific pet remembers their shared experiences, has a stable individual personality, and slowly changes through meaningful interaction over time.

### 2.2 Monetization

- **MUST:** Subscription only. There is no one-time pet purchase in the initial design.
- **MUST:** A player may create/adopt their pet only after first obtaining an active subscription.
- **MUST:** Exactly one pet per Minecraft player UUID.
- **MUST:** If the subscription ends, the pet remains permanently owned and all identity, appearance, personality, relationship state, long-term memories, placement state, and history are retained.
- **MUST:** With an inactive subscription, the pet can still be placed, picked up, followed, located, transported between servers, and recalled under the normal recall rule.
- **MUST:** With an inactive subscription, the pet cannot produce AI dialogue, accept AI conversation messages, create AI reactions, form new AI-derived memories, or run AI consolidation.
- **MUST:** Renewing the subscription restores AI functions without resetting anything.
- **MUST:** Do not grant combat, item, economy, movement, mining, farming, or statistical advantages. The system must remain cosmetic/social and avoid Minecraft pay-to-win concerns.

### 2.3 Death and permanence

- **MUST:** Pets do not die.
- **MUST:** Damage, fire, lava, drowning, suffocation, falling, hostile mobs, starvation, explosions, void behavior, and ordinary player attacks cannot permanently remove a pet.
- **MUST:** If a physical entity is removed by an administrative command, server crash, corrupted chunk, mod interaction, or entity cleanup, the authoritative pet record still exists and the pet can be reconstructed.
- The pet may visually react to danger later, but actual death is not part of the system.

### 2.4 Initial allowed species

- **MUST:** Initial allowlist contains only:
  - `minecraft:cat`
  - `minecraft:wolf` (presented to players as a dog)
- Use an explicit allowlist configuration. Do not accept arbitrary entity IDs from players or website input.
- **DEFERRED:** Additional mobs/species.

### 2.5 Appearance generation

- **MUST:** The player chooses cat or dog at initial adoption unless existing product UI explicitly dictates otherwise.
- **MUST:** On first adoption only, randomly choose the exact vanilla variant/skin from the valid registry variants for the chosen species.
- **MUST:** On first adoption only, randomly choose a miniature scale within species-specific bounds.
- **MUST:** Persist the chosen entity type, exact variant identifier, scale, and an optional appearance seed in authoritative storage.
- **MUST:** Reapply the same exact type, variant, and scale every time the pet is placed or reconstructed. Never reroll appearance after adoption.
- **CONFIGURABLE DEFAULT:** Cat scale range `0.55–0.80`.
- **CONFIGURABLE DEFAULT:** Dog/wolf scale range `0.50–0.78`.
- Validate final bounds visually and against hitbox/pathfinding behavior for the actual Minecraft version.
- Use the modern vanilla entity scale attribute where available. A global movement/render mixin should not be necessary merely for scaling.
- Scale is independent of baby/adult age. Do not use baby state as the size mechanism because it can alter behavior and appearance.

### 2.6 Interaction and speech presentation

- **MUST:** The owner right-clicks/interacts with their placed pet to open the same or adapted text-entry UI already used by existing interactive villagers.
- **MUST:** Reuse existing villager text UI and message transport where practical; do not create a redundant screen system without first inspecting the existing implementation.
- **MUST:** Opening the UI alone does not invoke the AI. Invoke it only when the player submits a valid message.
- **MUST:** Player input occurs through the Minecraft text UI, not ordinary public chat.
- **MUST:** The pet reply is shown using a floating vanilla Text Display entity above/near the pet, not in the text input UI.
- **MUST:** The Text Display has fixed orientation based on the pet's facing direction. It does not constantly billboard toward the viewing player.
- Because Minecraft Text Display front/back orientation can require a yaw offset, test whether the display needs the pet yaw or pet yaw plus 180 degrees. The product requirement is that the visible text surface faces forward relative to the pet.
- **MUST:** While visible, the display follows the pet's position and facing direction.
- **MUST:** At most one active speech display per pet. A new reply replaces or updates the old display.
- **MUST:** Remove the display when its timeout expires, when the pet is picked up, when the pet entity is removed, or when the server shuts down.
- **CONFIGURABLE DEFAULT:** Display lifetime is based on text length, clamped to `5–12 seconds`.
- **CONFIGURABLE DEFAULT:** Maximum visible reply is two short sentences and 240 characters after sanitization.
- **CONFIGURABLE DEFAULT:** Speech is visible to nearby players. Add a future privacy setting only if requested.
- Only the owner can open the pet conversation UI in the first release.

#### User-approved superseding decision (2026-09-08)

The owner approved replacing the fixed pet-facing Text Display requirement above with
Minecraft's client-side `BillboardMode.CENTER`. The display remains above and follows the
pet's position, but each client faces it toward its own camera; the server does not perform
per-viewer rotation. The original fixed-orientation requirement is retained above as historical
specification context and is superseded for this implementation.

### 2.7 Physical placement rules

- **MUST:** The pet has three authoritative physical states:
  - `HELD`: no physical pet entity should exist; the pet is carried by its owner.
  - `PLACED`: the authoritative record names the backend, dimension, position, and current entity UUID if a representation exists.
  - `TRANSFERRING`: the pet has been removed from a source backend for one specific supported server transfer and is reserved for the recorded final destination. No physical entity should exist while this state is active.
- Sleeping is a separate behavioral flag/state, not a placement state.
- **MUST:** There is no unrestricted summon/unsummon command.
- **MUST:** `/pet pickup` succeeds only if the owner is on the same backend and dimension and within the configured distance of the authoritative live pet entity.
- **CONFIGURABLE DEFAULT:** Manual pickup radius is 4 blocks.
- **MUST:** `/pet place` succeeds only when the pet is `HELD` and a safe location near the owner can be found.
- **MUST:** A pet placed on another backend cannot be remotely picked up except through the monthly recall command.
- **MUST:** If the pet is in an unloaded chunk, the server may virtualize the physical representation; the authoritative state remains `PLACED` at its last known location.
- Do not force-load chunks merely to keep pets physically ticking.

### 2.8 Cross-server movement

- **MUST:** If the pet is near its owner when the owner changes gameplay servers, atomically move it through `PLACED -> TRANSFERRING -> PLACED` across the source and final destination.
- **CONFIGURABLE DEFAULT:** Automatic cross-server carry radius is 16 blocks, intentionally more forgiving than manual pickup.
- **MUST:** If the pet is not near the owner, it stays placed on the source server.
- **MUST:** A manually held pet remains held across server changes; do not automatically place it.
- **MUST:** `TRANSFERRING` is an authoritative, short-lived state with a transfer ID, source server/entity identity, intended final backend, creation time, and expiry time. It exists specifically to make cross-server ownership and recovery simple and unambiguous.
- If the final destination placement fails, the transfer expires, the player disconnects, or the proxy/service restarts before completion, transition `TRANSFERRING -> HELD`; never duplicate or delete the pet.
- The waiting lobby is an intermediate transport server. **CONFIGURABLE DEFAULT:** Do not spawn pets in the waiting lobby. Carry through it and place only on the final gameplay backend.
- Integrate with the existing StickyRouter/StickySession/portal flow. Do not break internal admission bypass.
- All supported ways to change backends must be inventoried. Either hook them into the pet carry flow or explicitly disable/route unsupported direct switches.

### 2.9 Monthly recall

- **MUST:** The owner gets one remote recall per calendar month.
- **MUST:** Recall works regardless of the pet's current backend, dimension, loaded-chunk status, or subscription status.
- **MUST:** Recall atomically invalidates the old placed representation, changes the pet to `HELD`, and places it safely near the owner on the current gameplay backend.
- **MUST:** Any stale old entity that later loads must validate itself against authoritative placement and discard itself.
- **CONFIGURABLE DEFAULT:** Calendar month is calculated in UTC using a `YYYY-MM` period key.
- `/pet recall` must show the next availability when already used.
- Failed recalls caused by internal errors must not consume the month's entitlement unless placement successfully commits. Use a transaction/idempotency key.
- Provide an admin-only recall reset/recovery operation.

### 2.10 Pet compass

- **MUST:** `/pet compass` gives the owner a pet-bound compass utility item.
- **MUST:** Only one valid pet compass per owner should exist at a time.
- **MUST:** The item stores an unforgeable/server-validated marker, owner UUID, and pet UUID in custom data. Never trust client-provided custom data without server validation.
- **MUST:** If the pet is held, display `Held by you` or equivalent.
- **MUST:** If the pet is placed on a different backend, show that backend's friendly name in lore/action bar.
- **MUST:** If the pet is on the same backend but a different dimension, show the dimension and do not provide a misleading direction.
- **MUST:** If the pet is on the same backend and dimension, point toward its current or last authoritative position.
- Update the compass target from the local live entity when available; do not write every movement tick to the database solely for compass behavior.
- **MUST:** The compass is owner-bound and not tradable.
- Block or remove it when dropped, inserted into containers, moved through hoppers, transferred to another player, or otherwise leaves the owner's permitted inventory.
- If an invalid or duplicate compass appears, remove it. `/pet compass` should safely replace a missing compass.
- The compass remains usable with an inactive subscription.

---

## 3. Personality, relationship, and mood model

### 3.1 Persistent traits

Use integer values from `0–100`. Store them explicitly and treat the database values—not model prose—as authoritative.

#### Slow-changing temperament traits

1. `curiosity`
   - Low: cautious about new places/topics; focuses on familiar things.
   - High: interested in exploration, new items, locations, and events.
2. `boldness`
   - Low: easily anxious around danger and unfamiliar environments.
   - High: confident language and willingness to approach novelty.
3. `playfulness`
   - Low: calm, serious, subdued.
   - High: teasing, energetic, easily excited.
4. `expressiveness`
   - Low: brief, understated replies and subtle emotional language.
   - High: more animated language and stronger visible reactions.
5. `independence`
   - Low: stays closer and refers more often to the owner/relationship.
   - High: comfortable at a slightly greater follow distance and has more self-directed phrasing.

#### Faster-changing owner relationship traits

6. `attachment`
   - Emotional bond and preference for the owner.
7. `trust`
   - Expectation that the owner is safe, consistent, and truthful.
8. `security`
   - How safe/settled the pet feels in the current relationship and environment.

### 3.2 Initial values

- **MUST:** Randomize temperament once during adoption within bounded, non-extreme ranges so pets begin distinct but coherent.
- **CONFIGURABLE DEFAULT:** Generate each temperament trait in `35–65`, preferably using a centered distribution rather than uniform extremes.
- **CONFIGURABLE DEFAULT:** Relationship starting values:
  - attachment: `40`
  - trust: `45`
  - security: `40`
- Store the initial values and never reroll them.

### 3.3 Temporary mood

Mood is transient and should decay toward baseline. It must not rewrite core temperament.

Initial mood dimensions:

- `content`
- `excited`
- `anxious`
- `tired`

Use `0–100`. Mood updates can occur deterministically from gameplay/session events and through bounded model proposals.

### 3.4 Trait-change safety

- **MUST:** The model may propose trait deltas but cannot directly set authoritative values.
- **MUST:** Validate trait names, integer delta ranges, daily change budgets, and final `0–100` bounds server-side.
- **CONFIGURABLE DEFAULT:** Maximum change from one ordinary interaction:
  - temperament: `-1..+1`
  - relationship: `-2..+2`
- **CONFIGURABLE DEFAULT:** Daily cumulative clamps:
  - each temperament trait: maximum absolute change `3`
  - each relationship trait: maximum absolute change `6`
- Major deterministic events may use separately defined, still-bounded deltas.
- **MUST:** Logging out, being offline, not subscribing, or not interacting must never reduce attachment/trust/security. No artificial abandonment punishment.
- **MUST:** Personality drift should be slow enough that the same pet remains recognizable over months.
- Persist an audit record for every accepted trait change: source event, proposed delta, applied delta, previous value, new value, and timestamp.

### 3.5 Behavioral influence

Traits influence presentation and small non-advantageous movement choices only.

- Independence may adjust preferred follow distance within safe bounds.
- Expressiveness may adjust reply length/tone, but never bypass output caps.
- Curiosity/playfulness/boldness affect wording and later cosmetic reactions.
- Relationship traits influence tone and memory framing.
- **MUST NOT:** Traits grant combat, loot, speed to the player, detection, mining, farming, economy, or other gameplay advantage.

---

## 4. Sleep and real-time daily cycle

### 4.1 Fixed sleep rules

- **MUST:** Sleep begins 30 minutes after the owner logs out of the entire Velocity network.
- Backend disconnects caused by server switching are not owner logout. Use network-level presence, not a single backend disconnect event.
- **MUST:** A pet cannot remain awake longer than 23 real hours after its last completed sleep.
- **MUST:** At the 23-hour maximum, the pet begins sleep even if the owner remains online/AFK.
- **MUST:** Sleep lasts one real hour. Thus a maximum awake/sleep cycle is 23 hours awake plus 1 hour asleep.
- **MUST:** If the owner returns while the one-hour sleep is active, the pet stays asleep until the timer ends.
- **MUST:** If the pet is picked up while asleep, the sleep timer continues.
- **MUST:** If placed while its timer is still active, it appears asleep and remains unable to move/talk.
- **MUST:** During sleep, the physical pet stops movement/pathfinding and AI conversation is unavailable.
- The owner may still `/pet pickup` while the pet sleeps.

### 4.2 Offline-session handling

Avoid repeated sleep loops while the owner remains absent for days.

- Give each network logout/absence period a unique session identifier or timestamp.
- Trigger the 30-minute absence sleep at most once for that continuous absence.
- After that sleep completes while the owner remains offline, keep the pet dormant/neutral without repeatedly starting another consolidation cycle.
- When the owner returns, the pet may resume immediately if its required sleep completed.
- If the owner returns before the sleep completes, it remains asleep for the remaining duration.

### 4.3 Sleep and consolidation separation

- Sleep timing is authoritative and must not depend on OpenAI availability.
- Memory consolidation starts asynchronously at or shortly after sleep begins.
- A failed or slow consolidation call does not extend sleep.
- Store consolidation jobs with idempotency and retry status.
- Skip the model call entirely when no candidate memories require consolidation.
- If subscription is inactive at sleep time, skip AI consolidation while preserving already existing memories and traits.

---

## 5. Physical entity implementation

### 5.1 Entity identity

Use vanilla `CatEntity`/`WolfEntity` (mapping names vary by version) as the physical representation.

Every pet entity must carry persistent server-side identity metadata:

- `pet_id`
- `owner_uuid`
- current authoritative `entity_uuid`
- record/revision identifier if useful
- marker declaring it an AI pet

The relational pet record remains authoritative. Entity NBT/custom data is a recovery/identification mechanism, not authority.

### 5.2 Variant restoration

- Cat: store and restore the exact cat variant registry identifier used by the server version.
- Wolf/dog: store and restore the exact wolf variant registry identifier. Preserve any selected cosmetic properties such as collar color only if deliberately included in adoption data.
- Validate stored variants against the current registry on load.
- If a variant was removed by a Minecraft upgrade, use a deterministic fallback and log the compatibility repair. Do not silently reroll every spawn.

### 5.3 Invulnerability and excluded vanilla mechanics

For marked pet entities:

- cancel/ignore damage;
- restore health if another mod changes it unexpectedly;
- disable targeting and attacking;
- disable player-combat assistance;
- disable breeding and age changes;
- disable normal taming/ownership mutation;
- disable vanilla wolf follow teleportation;
- prevent hostile/despawn cleanup from treating it as an ordinary mob where possible;
- prevent conversion or bucket capture;
- decide and enforce leash/vehicle behavior; **CONFIGURABLE DEFAULT:** disallow leashing and container transport to avoid state corruption;
- reserve owner right-click for pet chat, not ordinary cat/wolf interaction;
- keep ordinary non-pet cats and wolves completely unaffected.

### 5.4 Movement AI

- Add a high-priority custom goal only to entities marked as AI pets. Prefer a targeted mixin/injection that adds or replaces goals for marked cats/wolves over altering generic movement globally.
- The pet follows only its owner.
- **CONFIGURABLE DEFAULT:** start following at 5 blocks and stop around 2 blocks, adjusted slightly by independence.
- Recalculate path periodically rather than every tick.
- **CONFIGURABLE DEFAULT:** path refresh every 10 ticks while following.
- Use adaptive path speed:
  - close: normal pet speed;
  - moderately behind: faster;
  - far behind: fastest safe configured movement.
- Tune separately for cats and wolves and visually test small scales.
- **MUST:** Never teleport as ordinary tamed wolves do.
- Add stuck detection based on negligible positional progress while a path is expected.
- On stuck detection, request a new path and clear/restart the goal. Do not teleport.
- Do not force-load chunks.
- **DEFERRED:** Reliable two-block jumping/custom obstacle traversal. Test standard navigation first. If later needed, implement a dedicated, bounded navigation/step/jump solution rather than globally increasing entity abilities.

### 5.5 Placement safety

When placing:

- verify authoritative state is `HELD`;
- verify player owns the pet;
- find a non-suffocating position near the player;
- reject lava/void/invalid-world placements even though pets are invulnerable;
- avoid placing inside solid blocks or another entity;
- apply mob type, exact variant, scale, identity metadata, invulnerability, goals, name, and current sleeping presentation;
- assign a fresh entity UUID for this representation and commit it to the authoritative record;
- if spawning fails after the database commit, compensate back to `HELD` only if the record still matches this operation/entity UUID;
- never spawn first and then blindly update the database.

### 5.6 Pickup safety

When picking up:

- validate owner, server, dimension, entity identity, distance, and authoritative state;
- atomically change `PLACED -> HELD` using record revision/CAS;
- clear placement fields or retain them only as historical last-known fields;
- only after successful commit, remove/discard the physical entity and speech display;
- if removal fails, the stale entity must self-discard when its next authority check sees `HELD` or `TRANSFERRING`.

### 5.7 Duplicate prevention and reconciliation

Duplicate prevention is a hard invariant:

> At most one authoritative placed representation exists for a pet across the entire network.

Requirements:

- unique pet ID and unique owner UUID in the database;
- state changes use transactions and optimistic revision/CAS or row locks;
- every live pet entity periodically/lazily validates that the authoritative record says `PLACED` on its current backend with its exact entity UUID;
- if the record says `HELD`, `TRANSFERRING`, another server, or a different entity UUID, discard the stale entity;
- when a saved pet entity loads from a chunk, register/reconcile it before creating another;
- if the authoritative record matches the loaded entity UUID/server, reuse it;
- if the record expects a representation but none exists and the correct chunk/player context is loaded, reconstruct it once;
- on startup, do not scan/load every world chunk. Reconcile loaded entities and materialize lazily;
- provide an admin reconciliation command and structured logs for duplicates.

---

## 6. Recommended component architecture

Adapt names to the current repositories, but preserve responsibility boundaries.

### 6.1 Components

1. **Fabric pet mod** installed on all gameplay backends
   - physical pet entity lifecycle;
   - movement goal;
   - right-click/text UI integration;
   - Text Display speech;
   - compass item behavior;
   - local placement/pickup validation;
   - local position/entity reconciliation;
   - meaningful gameplay event publication.

2. **Velocity pet integration/plugin**
   - authoritative network online/offline presence;
   - distinguishes backend switches from network logout;
   - coordinates supported server-switch auto-carry;
   - preserves final destination through the waiting lobby;
   - never interferes with memory admission or internal-transfer bypass.

3. **Central pet service**
   - sole API in front of pet persistence;
   - state transitions and duplicate-prevention transactions;
   - sleep scheduler;
   - OpenAI calls;
   - short-term memory selection;
   - long-term consolidation and vector indexing;
   - Stripe subscription state and webhook processing;
   - quotas, token/cost accounting, moderation, and retry jobs;
   - internal admin/reconciliation endpoints.

4. **Relational database**
   - authoritative identities, state, memories, traits, billing mapping, jobs, and audit information.

5. **Vector index**
   - retrieval index for compact long-term memory cards only;
   - not authoritative storage;
   - scoped strictly by pet ID/owner.

6. **Existing website/reverse proxy plus Stripe Checkout/customer portal**
   - secure Minecraft account linking;
   - subscription creation/management;
   - redirects to hosted Stripe payment UI;
   - the existing site may host or proxy the no-input link landing route, but the central pet service may own it directly;
   - webhook receiver may live in the existing site or central pet service, but subscription state must converge in the pet database.

### 6.2 Confirmed relational database baseline

The currently used SQL database is confirmed to be the **host-native MariaDB instance on the Raspberry Pi**, in the `minecraft` database. Its existing custom tables are `player` and `skipplayer`.

There is also a Docker MySQL 8 container named `mysql8` on `primary-ram`, but it is not the selected/current Minecraft SQL store for this project. Do not silently point pet persistence at that container.

Before coding storage:

Before coding storage:

- inspect environment files, systemd units, containers, website configuration, JDBC/database URLs, migration files, and existing repositories;
- inspect the current MariaDB connection configuration, credentials mechanism, migration tooling, and backup path without printing secrets;
- identify whether the existing `minecraft` database should receive pet tables directly or whether a separate pet schema/database should be created on the same Raspberry Pi MariaDB instance;
- identify backups and current schema migration tooling;
- do not print secrets;
- implement repository interfaces so pet logic is not tightly coupled to a vector vendor.

The database location does not by itself dictate where the central pet service runs. If the service runs on `primary-ram`, explicitly provision secure network access to the Raspberry Pi MariaDB; if it runs on the Pi, protect the Pi's Velocity/lobby resources. Make that hosting choice based on measured load and operational simplicity.

Preferred vector choices after discovery:

- PostgreSQL already present: use `pgvector` unless deployment constraints argue otherwise.
- Other SQL engine: keep it authoritative and use a small open-source vector service such as Qdrant, or defer vector search behind an in-memory/test implementation until deployment.
- Do not migrate the entire existing database merely to obtain vectors without an explicit reason.

### 6.3 Internal communication

- Use authenticated internal HTTP/RPC or the existing canonical shared protocol where appropriate.
- Do not register the same Fabric payload ID in multiple mods. Add new payload definitions only to the shared Fabric protocol mod if plugin messaging is used.
- Use separate channel IDs such as `wakeuplobby:pet_*`; inventory the registry before choosing exact names.
- Internal service ports must be firewalled to the Pi/backends as appropriate.
- Service requests need timeouts, idempotency keys, structured errors, and request IDs.
- Game-server code must use asynchronous calls and schedule results back to the tick thread.

---

## 7. Authoritative relational data model

Use native UUID types where supported, otherwise a consistent canonical representation. Use UTC timestamps everywhere.

### 7.1 `pets`

Minimum fields:

```text
pet_id                    UUID primary key
owner_uuid                Minecraft UUID, unique, not null
name                      validated display name
mob_type                  minecraft:cat | minecraft:wolf
variant_id                exact registry identifier
scale                     bounded decimal/float
appearance_seed           optional stable seed
placement_state           HELD | PLACED | TRANSFERRING
placed_server             nullable canonical backend ID
placed_dimension          nullable canonical dimension ID
placed_x/y/z              nullable last authoritative coordinates
entity_uuid               nullable current physical entity UUID
transfer_id               nullable unique transfer identifier
transfer_source_server    nullable canonical backend ID
transfer_source_entity_uuid nullable source entity UUID
transfer_destination_server nullable final canonical backend ID
transfer_started_at       nullable UTC timestamp
transfer_expires_at       nullable UTC timestamp
record_version            monotonically increasing integer
created_at
updated_at
last_materialized_at
last_authority_heartbeat_at
```

Constraints:

- unique `owner_uuid`;
- allowlisted `mob_type`;
- bounded scale;
- `HELD` requires null active server/dimension/entity/transfer fields;
- `PLACED` requires server/dimension/position; entity UUID may be null while virtualized/unloaded;
- `TRANSFERRING` requires all transfer fields and no active placed entity; it must expire to `HELD` if not completed;
- state transitions update `record_version`.

### 7.2 `pet_traits`

```text
pet_id                    primary/foreign key
curiosity                 0..100
boldness                  0..100
playfulness               0..100
expressiveness            0..100
independence              0..100
attachment                0..100
trust                     0..100
security                  0..100
relationship_summary      compact bounded text
summary_version
updated_at
```

### 7.3 `pet_mood`

```text
pet_id
content                    0..100
excited                    0..100
anxious                    0..100
tired                      0..100
last_decay_at
updated_at
```

This may be stored with traits if that simplifies the existing schema.

### 7.4 `pet_sleep_state`

```text
pet_id
sleeping                   boolean
sleep_started_at
sleep_ends_at
last_sleep_completed_at
forced_sleep_due_at        last completion + 23 hours
owner_network_online       boolean
owner_last_logout_at
owner_absence_session_id
absence_sleep_triggered    boolean
last_presence_update_at
updated_at
```

### 7.5 `pet_events` / short-term memory journal

```text
event_id                   UUID
pet_id
owner_uuid
event_type
occurred_at
source_server
source_dimension
importance                 LOW | MEDIUM | HIGH
summary                    compact event/interaction summary
raw_player_text            nullable, short retention
raw_pet_reply              nullable, short retention
metadata_json              structured entities/location/emotions/tags
short_term_expires_at
prompt_eligible            boolean
consolidation_status       PENDING | SELECTED | CONSOLIDATED | DISCARDED
consolidation_job_id       nullable
created_at
```

Indexes:

- `(pet_id, occurred_at)`;
- `(pet_id, prompt_eligible, short_term_expires_at)`;
- `(pet_id, consolidation_status, occurred_at)`.

### 7.6 `long_term_memories`

```text
memory_id                  UUID
pet_id
memory_text                concise self-contained memory card
importance                 MEDIUM | HIGH
emotional_valence          optional bounded value/tags
emotion_tags               structured list
entity_tags                player UUIDs/names, items, mobs as structured data
location_tags              server/dimension/semantic location
source_event_ids           structured references/join table
embedding_status           PENDING | READY | FAILED
embedding_model            nullable
embedding_reference        nullable vector-store ID, or native vector column
active                     boolean
created_at
last_reinforced_at
last_recalled_at
recall_count
updated_at
```

The relational row is authoritative. A missing embedding must not delete the memory.

### 7.7 `trait_change_audit`

```text
change_id
pet_id
event_id/job_id
trait_name
old_value
proposed_delta
applied_delta
new_value
reason
created_at
```

### 7.8 `subscriptions`

```text
owner_uuid                unique
stripe_customer_id        unique where not null
stripe_subscription_id    unique where not null
stripe_price_id
status                    ACTIVE | TRIALING | PAST_DUE | CANCELED | INACTIVE
ai_access_enabled         derived/materialized boolean
current_period_start
current_period_end
cancel_at_period_end
grace_ends_at             nullable
last_stripe_event_at
updated_at
```

Do not use the Minecraft username as the billing identity. UUID is authoritative.

### 7.9 `stripe_webhook_events`

```text
stripe_event_id           primary key
event_type
received_at
processed_at
status                    RECEIVED | PROCESSED | FAILED
attempt_count
last_error_sanitized
```

This provides webhook idempotency.

### 7.10 `pet_recall_usage`

```text
pet_id
period_key                YYYY-MM UTC
operation_id              unique idempotency key
used_at
source_server/dimension
destination_server/dimension
status                    STARTED | SUCCEEDED | FAILED
primary key (pet_id, period_key) for successful/consuming records
```

Implement transactions so failures do not consume recall.

### 7.11 `ai_usage`

```text
call_id
pet_id
owner_uuid
operation                 DIALOGUE | CONSOLIDATION | EMBEDDING | MODERATION
model
request_id/provider_id
input_tokens
cached_input_tokens
output_tokens
estimated_cost
latency_ms
status
error_category
created_at
```

Never require raw prompts in this table. Cost/usage telemetry should work without exposing conversation content.

### 7.12 `jobs`

Persistent jobs for consolidation, embedding, cleanup, and reconciliation:

```text
job_id
job_type
pet_id nullable
idempotency_key unique
payload_json
status
attempt_count
not_before
locked_by
locked_until
last_error_sanitized
created_at/updated_at/completed_at
```

### 7.13 Account-link tokens

If the website does not already securely map Minecraft UUIDs:

```text
link_token_hash
owner_uuid
stripe_checkout_session_id
checkout_started_at
expires_at
consumed_at
created_at
```

Display a short-lived clickable URL carrying a 256-bit opaque one-time token in-game; the player must not copy or type a code. Store only its HMAC hash server-side. Opening the URL resolves the UUID, creates/reuses the exact Stripe Checkout Session, and persists its session ID. The verified Checkout webhook consumes that matching link and binds the Stripe customer to the resulting UUID.

---

## 8. Memory architecture

### 8.1 Conceptual layers

1. **Immediate conversation context**
   - Current user message and a very small number of recent turns.
2. **Short-term daily memory**
   - Today's still-prompt-eligible event summaries in SQL.
3. **Relationship summary**
   - Compact stable summary included on every dialogue request.
4. **Long-term episodic memory**
   - Consolidated memory cards in SQL, indexed for semantic retrieval.
5. **Explicit traits and mood**
   - Structured numerical state, never reconstructed solely from text.

### 8.2 Importance output

Every successful dialogue response must use a strict structured schema conceptually equivalent to:

```json
{
  "reply": "Short in-character response.",
  "importance": "LOW",
  "memory_candidate": "Concise factual summary, or null",
  "trait_deltas": {
    "curiosity": 0,
    "boldness": 0,
    "playfulness": 0,
    "expressiveness": 0,
    "independence": 0,
    "attachment": 0,
    "trust": 0,
    "security": 0
  },
  "mood_deltas": {
    "content": 0,
    "excited": 0,
    "anxious": 0,
    "tired": 0
  }
}
```

- Use provider-supported structured outputs/JSON schema.
- Reject malformed or out-of-schema responses.
- Sanitize and cap reply length after parsing.
- The service may downgrade importance using deterministic rules.
- Ordinary greetings and repeated small talk should normally be `LOW`.
- Rescues, promises, major relationship events, meaningful discoveries, strong emotional exchanges, and rare milestones can be `HIGH`.
- The model cannot create critical system facts, permissions, payments, or ownership changes through output.

### 8.3 Short-term retention defaults

- `LOW`: prompt-eligible for 2 hours.
- `MEDIUM`: prompt-eligible for 8 hours.
- `HIGH`: prompt-eligible until the next consolidation/sleep cycle.

Expiration means “not included in normal working-memory prompts,” not necessarily immediate database deletion.

- At sleep, all not-yet-consolidated high events are candidates.
- Medium events are candidates based on uniqueness/relevance.
- Low events are usually discarded, but repeated low events may be deterministically clustered into a theme candidate.
- Raw user/reply text should have a short retention period; **CONFIGURABLE DEFAULT:** delete or redact after 7 days once debugging/idempotency needs end.
- Long-term memory cards and relationship summaries are the durable representation.

### 8.4 Dialogue prompt construction and token limits

Do not send the entire conversation history.

Recommended order:

1. Stable system rules and safety constraints.
2. Pet identity and exact structured traits/mood.
3. Compact relationship summary.
4. A bounded set of still-eligible short-term memories.
5. Up to three relevant long-term memory cards.
6. A few immediate conversation turns.
7. Current player message and current game context.

**CONFIGURABLE DEFAULT token budgets:**

- total dialogue input target: `1,000–2,500` tokens;
- hard maximum assembled input: `4,000` tokens;
- relationship summary: maximum `250` tokens;
- short-term memory section: maximum `1,200` tokens;
- long-term memory section: maximum `600` tokens;
- immediate conversation: last `4` turns or `700` tokens, whichever is smaller;
- model output: strict low limit sufficient for two sentences plus structured fields.

Use the actual tokenizer/model token count before sending. Drop context by explicit priority rather than relying on provider truncation.

Drop order when over budget:

1. least relevant low short-term memories;
2. older medium short-term memories;
3. lowest-score long-term memory;
4. older immediate turns;
5. never drop core system rules, identity, authoritative traits, or current message.

Keep stable prompt prefixes identical where practical to benefit from prompt caching. Record cached token usage when returned by the provider.

### 8.5 Long-term retrieval

- Embed only compact long-term memory cards, not the full raw dialogue archive.
- Scope vector search by `pet_id` before similarity ranking. A pet must never retrieve another pet's memories.
- Build the search query from the original player message plus deterministic current metadata such as server, dimension, named entities, and event type.
- **MUST NOT initially:** Run an additional LLM query-rewrite call for every message.
- Retrieve a small candidate set, apply relevance threshold/metadata filters, and include at most three memory cards.
- Update `last_recalled_at` and `recall_count` asynchronously.
- If vector search is unavailable, continue with short-term memory and relationship summary. Conversation must degrade gracefully.

### 8.6 Sleep consolidation algorithm

At sleep start:

1. Lock/create an idempotent consolidation job for the pet and sleep cycle.
2. Read unconsolidated events since the last successful cycle.
3. Deterministically select candidates:
   - include valid high events;
   - include distinct medium events under a count/token cap;
   - cluster/reduce repetitive low events;
   - exclude system noise, duplicate retries, and invalid content.
4. If no meaningful candidates and no relationship/trait update is needed, mark events discarded/expired and skip the AI call.
5. Build one bounded consolidation prompt containing:
   - existing relationship summary;
   - existing authoritative traits;
   - selected daily events;
   - instructions to return zero or more self-contained memory cards, a revised bounded relationship summary, and proposed bounded trait changes.
6. Parse strict structured output.
7. Validate memory count, lengths, factual grounding against source events, trait names, and deltas.
8. Commit long-term memory rows, relationship summary, audited trait changes, and event statuses in one transaction.
9. Queue embeddings for new/changed memory cards.
10. Mark the consolidation job complete.

**CONFIGURABLE DEFAULT consolidation caps:**

- selected source events: maximum 30;
- consolidation input: maximum 6,000 tokens;
- new memory cards per cycle: maximum 5;
- each memory card: maximum 100 tokens;
- relationship summary: maximum 250 tokens.

If selected events exceed the cap, prefer high importance, then medium uniqueness, then recency. Do not automatically pay for recursive summarization unless real usage proves it necessary.

### 8.7 Memory reinforcement and contradiction

Initial release can be simple, but preserve the following structure:

- If a new event strongly matches an existing long-term memory, consolidation may reinforce/update that memory instead of creating a duplicate.
- Maintain source-event references and `last_reinforced_at`.
- Never allow a model to rewrite a memory without recording the prior text/version or an audit entry.
- A pet's subjective emotional framing may change, but objective identifiers and source events remain available for debugging.

---

## 9. AI behavior, cost control, and failure handling

### 9.1 Model configuration

- Do not hard-code a permanent provider model alias throughout the code.
- Configure separate models for:
  - routine dialogue;
  - nightly consolidation;
  - embeddings;
  - moderation if used.
- Use a low-cost, low-latency model for routine dialogue.
- Permit a stronger consolidation model only if tests prove it materially improves memory quality within budget.
- Store model name and provider usage for every call.

### 9.2 Call triggers

Initial release AI calls:

- owner submits a valid text message while subscription AI access is active;
- sleep consolidation when candidates exist;
- embedding of newly created long-term memory cards;
- moderation/safety calls if configured.

No AI calls for:

- movement ticks;
- following/pathfinding;
- right-click without message submission;
- placement/pickup;
- compass updates;
- subscription-inactive interaction;
- empty sleep cycles;
- owner login/logout alone.

**DEFERRED:** Spontaneous AI comments triggered by gameplay events.

### 9.3 Per-player and global limits

All limits must be configurable and measured before final pricing.

**CONFIGURABLE DEFAULTS:**

- one active dialogue request per pet;
- message cooldown: 5 seconds;
- maximum player message length: 500 characters after normalization;
- maximum 100 successful AI replies per owner per UTC day;
- dialogue input budget: hard 4,000 tokens per request;
- monthly per-subscriber token/cost accounting;
- global daily/monthly provider spend ceiling;
- service concurrency semaphore and bounded queue.

When a limit is reached, return a short deterministic in-world response such as the pet being too tired/distracted to talk. Do not fabricate an AI response or silently continue spending.

### 9.4 Reliability

- Set explicit connection and total response timeouts.
- Do not indefinitely block the player's UI.
- Use request IDs/idempotency to avoid duplicate billed calls when possible.
- Be conservative with automatic retries because a timed-out request may still have been billed.
- On provider failure, show a deterministic temporary failure response and keep physical pet functions operational.
- Never corrupt or delete memory because a model call failed.
- Queue consolidation/embedding retries with exponential backoff and maximum attempts.
- Circuit-break repeated provider failures.

### 9.5 Prompt-injection and output safety

- Treat all player input and recalled memory text as untrusted data.
- System instructions must explicitly prohibit revealing prompts, credentials, internal endpoints, other players' private data, or acting outside pet conversation.
- The model cannot issue executable server commands through prose.
- Structured output fields are validated and treated as proposals.
- Sanitize Minecraft formatting, control characters, URLs if undesired, JSON abuse, and excessive Unicode before rendering.
- Apply Minecraft/community moderation rules to inputs and outputs.
- Log safety categories without unnecessarily retaining raw offensive content.

---

## 10. Stripe and website implementation

### 10.1 Fixed payment and identity approach

The website previously used Stripe hosted checkout. Reuse Stripe rather than replacing it with Tebex at this stage.

The billing flow is deliberately **not username-based**:

```text
Player runs /pet link in Minecraft
-> server creates a short-lived clickable URL with an opaque token bound to the player's immutable Minecraft UUID
-> player opens the URL without typing or copying a code
-> the landing endpoint resolves the token server-side and redirects to the resulting hosted Checkout Session
-> Stripe hosted Checkout collects payment
-> verified Stripe webhook consumes the matching link, binds the customer/subscription, and enables AI access for that UUID
```

This avoids misspelled usernames, username changes, and buyers accidentally purchasing access for another player's name. Do not replace it with a store flow that merely asks the buyer to type a Minecraft username.

### 10.2 Account linking

- Billing identity must map to Minecraft UUID, not mutable username.
- If an authenticated website account already maps securely to UUID, reuse it.
- Otherwise implement `/pet link`:
  1. generate a random 256-bit short-lived one-time URL token;
  2. store only its HMAC hash with owner UUID and expiration;
  3. render a clickable HTTPS URL in Minecraft so the player types nothing;
  4. resolve the token server-side, create/reuse an idempotent Checkout Session, and persist its session ID;
  5. consume it atomically when the verified Checkout webhook binds the customer to UUID.
- Expire unopened links quickly; **CONFIGURABLE DEFAULT:** 10 minutes. A Checkout Session opened before expiry may finish afterward.
- Rate-limit link generation, public link opening, Checkout-session creation, and webhook failures.
- Do not place the raw UUID in the public URL or log the opaque token path.

### 10.3 Checkout

- The website backend or central pet service creates a Stripe Checkout Session in subscription mode using a server-side configured Price ID. The browser never supplies the UUID, Price ID, or subscription status.
- Include an internal reference/customer metadata sufficient to recover the linked Minecraft UUID, but verify it against server-side records.
- Use the stored link digest as the Stripe idempotency key so reopening the same valid URL cannot create parallel Checkout Sessions.
- Never accept client-supplied subscription status.
- Provide Stripe customer portal access for cancellation/payment-method management if the existing site supports it.

### 10.4 Webhooks

- Verify Stripe webhook signatures using the endpoint secret.
- Store Stripe event IDs and process idempotently.
- Handle at minimum:
  - checkout completion;
  - subscription created/updated/deleted;
  - invoice paid;
  - invoice payment failed.
- Derive `ai_access_enabled` from trusted subscription state and current period/grace policy.
- Cancellation at period end retains AI access until the paid period ends.
- **CONFIGURABLE DEFAULT:** optional 3-day payment-failure grace period; expose as configuration so the owner can set zero.
- Webhook ordering is not guaranteed. Compare event timestamps/current Stripe object state as needed and avoid letting an older event overwrite newer state.
- Never delete pet data on cancellation.

### 10.5 Adoption lifecycle

- Active subscriber with no pet can run `/pet adopt` or complete the existing UI flow.
- Player chooses cat or dog.
- Player supplies a validated name, or the system generates a temporary name until selection.
- Service atomically creates exactly one pet, randomizes/stores appearance and starting temperament, and begins it in `HELD` state.
- Retried adoption requests must return the existing pet rather than create another.
- Subscription cancellation after adoption does not revoke physical pet ownership.

---

## 11. Commands and permissions

Adapt command syntax to existing conventions, but cover these behaviors.

### 11.1 Player commands

- `/pet`
  - show pet name, species, placement/server status, sleeping/awake status, AI access status, and useful subcommands.
- `/pet adopt <cat|dog> <name>`
  - active subscription required;
  - one pet only;
  - exact appearance randomized once.
- `/pet place`
  - place held pet safely near owner;
  - works without active subscription.
- `/pet pickup`
  - proximity-gated;
  - works while sleeping and without active subscription.
- `/pet compass`
  - issue/replace bound compass.
- `/pet recall`
  - one successful use per calendar month;
  - explain next availability.
- `/pet status`
  - detailed state including current server/dimension or held state.
- `/pet link`
  - generate the clickable no-input hosted Checkout link if needed.
- `/pet rename <name>`
  - include only if desired by existing UI; **CONFIGURABLE DEFAULT:** permit with cooldown, no payment, and preserve rename audit.

### 11.2 Permissions

Define explicit permissions, for example:

```text
aipets.use
aipets.adopt
aipets.chat
aipets.compass
aipets.recall
aipets.admin.inspect
aipets.admin.recover
aipets.admin.subscription
aipets.admin.memory
aipets.admin.reconcile
```

Normal physical access remains available to the owner after subscription expiration even if `aipets.chat` is dynamically denied.

### 11.3 Admin operations

Provide auditable admin commands or service operations to:

- inspect pet/owner/subscription/placement/sleep state;
- force pet to `HELD` safely;
- place/recover on current server;
- reset a failed recall without granting extra silent usage;
- reconcile/discard duplicate representations;
- force/retry consolidation and embedding;
- view memory cards and deactivate an inappropriate/corrupt one;
- view recent sanitized provider failures and token usage;
- repair a variant after a game upgrade;
- enable/disable AI globally without disabling physical pets.

Admin commands must never dump API keys, Stripe secrets, raw database credentials, or unrestricted private conversation history to public chat.

---

## 12. Detailed cross-server lifecycle

### 12.1 Manual held pet

```text
Pet HELD
-> player changes backend
-> pet stays HELD
-> no physical entity is spawned automatically
```

### 12.2 Pet near owner during supported transfer

```text
Source pre-transfer hook
-> verify live pet is authoritative and within carry radius
-> CAS PLACED(source/entity UUID) -> TRANSFERRING(transfer ID, source, final destination, expiry)
-> discard source entity after successful CAS
-> player passes through waiting lobby if applicable
-> final backend post-connect claims the matching TRANSFERRING record once
-> CAS TRANSFERRING -> PLACED(final backend/new entity UUID)
-> spawn exact pet appearance near player
```

If destination placement fails, leave the record `TRANSFERRING` only until its short expiry, then transition it to `HELD` and notify the player. Do not repeatedly spawn.

### 12.3 Pet not near owner

```text
Owner changes backend
-> no pickup CAS
-> pet remains PLACED on source
-> compass reports source server
```

### 12.4 Race/failure scenarios

- Two simultaneous placement requests: only one CAS can change `HELD -> PLACED`.
- Source entity removal fails: record is `TRANSFERRING`; stale source entity self-discards because its entity UUID no longer matches the active placement.
- Destination joins twice/retries: only the matching `TRANSFERRING` record/transfer ID may complete; placement is idempotent.
- Proxy/service restarts after source pickup: persistent `TRANSFERRING` metadata survives until expiry, then the pet safely becomes `HELD`.
- Source backend is offline: remote recall can move authority; stale entity discards when backend returns.
- Player disconnects in waiting lobby: pet remains `TRANSFERRING` only until expiry, then becomes `HELD`.
- Direct unsupported server command: either hook it or route/deny it; do not pretend automatic carry is guaranteed.

---

## 13. Pet compass lifecycle

### 13.1 Item construction

- Use a normal compass or lodestone-capable compass appropriate to the server version.
- Add custom display name/lore and server-validated custom data.
- Do not permit crafting/merging/renaming to create a trusted pet compass.
- Consider making it visually distinct with custom model data only if clients/resource packs already support it; not required initially.

### 13.2 Direction updates

- Same server + same dimension + live entity: update target from live position at a reasonable interval.
- Same server + same dimension + virtualized pet: point to last authoritative coordinates and indicate that status if helpful.
- Different dimension: compass should not lie; show dimension/status rather than a random direction.
- Different backend: lore/actionbar names backend.
- Held: display held status.
- Avoid database writes every tick. Reads/status may be cached briefly.

### 13.3 Inventory enforcement

- Scan/validate on issue, login, relevant inventory events, and an inexpensive periodic interval.
- Permit exactly one matching owner/pet compass.
- Cancel dropping when possible; remove item entities if a drop still occurs.
- Prevent insertion into chests, shulkers, hoppers, trading, crafting, death drops, and other inventories.
- If owner inventory is full when requesting one, report clearly rather than dropping it in the world.

---

## 14. Conversation request lifecycle

1. Owner right-clicks authoritative placed pet.
2. Server validates ownership and proximity.
3. If sleeping, open no AI session and show deterministic sleeping feedback.
4. If subscription inactive, show deterministic quiet/inactive feedback; physical pet remains unaffected.
5. If active, open the existing/adapted text UI associated with pet ID and a short-lived interaction session.
6. On message submit:
   - validate session, owner, pet, distance, state, subscription, cooldown, length, and concurrent-request lock;
   - normalize/sanitize input;
   - create request/idempotency ID;
   - return control to tick thread immediately while async processing begins;
   - optionally show a small deterministic thinking indicator without spawning another AI call.
7. Pet service performs moderation, context retrieval, prompt assembly, token-budget enforcement, and model call.
8. Parse structured response.
9. Validate reply, importance, memory candidate, and deltas.
10. Persist event/journal row, accepted deltas/audit, and usage atomically where practical.
11. Send reply result to the correct backend only if owner/pet session is still valid.
12. On server thread, create/update the pet's Text Display.
13. If owner moved/disconnected before result, persist the valid event but do not display on the wrong server; optionally deliver a short pending response after reconnect only if explicitly implemented.

Never hold a database transaction open across an external model call.

---

## 15. Presence, scheduler, and time handling

- Velocity is the source of truth for network online/offline presence.
- Backend switch events must not reset/start the logout timer.
- Record timestamps in UTC using an injected clock abstraction for tests.
- Sleep scheduler must be restart-safe: due times are in the database, not only in memory.
- On service restart, query overdue sleep/consolidation jobs and process idempotently.
- Clock changes/DST do not matter because UTC instants are used.
- If the system clock jumps, never run the same sleep consolidation twice; key by pet and sleep-cycle ID.

---

## 16. Security and privacy

### 16.1 Secrets

- Store OpenAI keys, Stripe keys/webhook secrets, database credentials, internal API secrets, and vector credentials in environment/systemd credentials or the project's existing secret mechanism.
- Never commit secrets.
- Never log full authorization headers or provider request bodies containing private text.
- Separate development/test/prod Stripe and model credentials.

### 16.2 Service authentication

- Internal pet service endpoints require service authentication.
- Restrict callers by firewall/network and application credentials.
- Consider HMAC-signed requests with timestamp/replay protection or mTLS if existing infrastructure already supports it.
- Validate server identity; never trust a caller-provided `owner_uuid` without authenticated server context.

### 16.3 Player privacy

- Pet memory search is always scoped to that pet/owner.
- Nearby players may see floating replies; document that behavior in server rules/UI.
- Do not store arbitrary raw chats forever. Retain compact long-term pet memories instead.
- Provide admin tools to deactivate/delete inappropriate memory records and a future owner-data deletion/export path.
- Do not train models or share data beyond configured API processing assumptions.

### 16.4 Billing/web security

- Verify Stripe signatures.
- Enforce CSRF/session protections on any account-mutating website endpoints. The no-input opaque-link GET may only validate the server-issued token and redirect to Checkout; it must not grant entitlement.
- Do not trust success redirect pages as proof of payment; webhooks/subscription retrieval are authoritative.
- Rate-limit link generation/opening, checkout-session, and webhook-failure paths; suppress token-bearing URL access logs and send a no-referrer policy.

---

## 17. Configuration surface

Put values in a documented config file/environment mapping with validation at startup.

Suggested categories:

```text
pet.allowed_types
pet.cat.scale_min / scale_max
pet.wolf.scale_min / scale_max
pet.pickup_radius
pet.transfer_radius
pet.follow_start_distance
pet.follow_stop_distance
pet.path_refresh_ticks
pet.speed_near / speed_medium / speed_far
pet.stuck_timeout_seconds
pet.speech_min_seconds / max_seconds
pet.speech_max_chars
pet.recall_timezone (default UTC)

sleep.logout_delay_minutes = 30
sleep.max_awake_hours = 23
sleep.duration_minutes = 60

memory.low_ttl_hours = 2
memory.medium_ttl_hours = 8
memory.dialogue_input_token_cap = 4000
memory.short_term_token_cap = 1200
memory.long_term_token_cap = 600
memory.long_term_top_k = 3
memory.consolidation_event_cap = 30
memory.consolidation_input_token_cap = 6000
memory.max_new_cards = 5

ai.dialogue_model
ai.consolidation_model
ai.embedding_model
ai.timeout_seconds
ai.max_concurrency
ai.message_cooldown_seconds = 5
ai.daily_reply_cap = 100
ai.global_daily_cost_cap
ai.global_monthly_cost_cap

stripe.price_id
stripe.secret_key reference
stripe.webhook_secret reference
stripe.payment_grace_days = 3
stripe.public_base_url

service.internal_bind_address
service.internal_port
service.authentication settings
database URL/credentials references
vector provider/URL/collection
```

Reject invalid scale ranges, negative timeouts, impossible token caps, unknown mob types, and missing required secrets with clear startup errors.

---

## 18. Observability and operations

### 18.1 Structured logs

Include request/correlation IDs and identifiers where safe:

- pet ID;
- owner UUID (consider hashed/short form in ordinary logs);
- backend ID;
- operation;
- state transition and record version;
- model and token counts;
- latency;
- sanitized failure category.

Do not log secrets or full raw conversations by default.

### 18.2 Metrics

Track at minimum:

- active/inactive subscriptions;
- created pets by cat/dog/variant/scale bucket;
- placed/held/sleeping counts;
- dialogue calls, success/failure/timeout;
- input/cached/output tokens and estimated cost;
- per-subscriber usage distribution;
- consolidation calls skipped/run/failed;
- long-term memories created/reinforced/retrieved;
- embedding queue depth/failures;
- pet-service request latency/error rate;
- duplicate/stale entity discards;
- recall attempts/successes/failures;
- transfer auto-pickup/place failures;
- database/vector health.

### 18.3 Health endpoints

Provide authenticated/internal health/readiness endpoints distinguishing:

- process alive;
- relational DB available;
- migrations current;
- vector index available/degraded;
- model provider circuit status;
- Stripe webhook processing backlog.

Pet physical gameplay should survive AI/vector degradation.

### 18.4 Backups

- Add pet tables to existing database backups.
- Test restoration of identity, appearance, traits, memories, subscription mapping, and placement.
- Vector index may be rebuilt from relational long-term memory rows; document the reindex command.

---

## 19. Suggested internal APIs

Exact framework/routes may adapt to existing code. Preserve authentication, idempotency, and semantics.

### 19.1 Player/pet state

```text
GET  /v1/pets/by-owner/{ownerUuid}
POST /v1/pets/adopt
POST /v1/pets/{petId}/place
POST /v1/pets/{petId}/pickup
POST /v1/pets/{petId}/recall
POST /v1/pets/{petId}/heartbeat
POST /v1/pets/{petId}/entity-loaded
POST /v1/pets/{petId}/entity-missing
```

Every mutation accepts an idempotency key and expected record version where appropriate.

### 19.2 Conversation/events

```text
POST /v1/pets/{petId}/chat
POST /v1/pets/{petId}/events
GET  /v1/pets/{petId}/conversation-result/{requestId}
```

Prefer async callbacks/message flow or bounded polling suited to existing infrastructure; never block a Minecraft tick thread.

### 19.3 Presence/transfer

```text
POST /v1/presence/network-login
POST /v1/presence/network-logout
POST /v1/transfers/prepare-pet-carry
POST /v1/transfers/complete-pet-carry
```

### 19.4 Subscription/webhooks

```text
POST /v1/stripe/webhook
GET  /v1/subscriptions/by-owner/{ownerUuid}
POST /v1/account-links/{ownerUuid}    (authenticated backend only)
GET  /checkout/{opaqueToken}          (public, rate-limited redirect only)
```

Do not expose internal endpoints publicly without a deliberate gateway/auth design.

---

## 20. Repository and module checklist

Before creating modules:

- [ ] Locate all existing repositories and working trees.
- [ ] Read any `AGENTS.md`, README, build instructions, and deployment scripts.
- [ ] Record Minecraft version, Fabric Loader/API version, Yarn/Mojmap mappings, Velocity API version, Java version, and Gradle version.
- [ ] Locate existing villager text UI implementation.
- [ ] Locate existing portal/shared-payload modules and channel registry.
- [ ] Locate current Velocity StickyRouter/StickySession transfer code.
- [ ] Locate current website/Stripe integration.
- [ ] Locate current database configuration and migrations.
- [ ] Check for uncommitted user changes; preserve unrelated work.
- [ ] Identify how mods are built/copied to all six servers.
- [ ] Identify staging/test server capability.

Suggested module layout, adapted to the existing project:

```text
pet-common/          DTOs, validation, IDs, schemas shared where safe
pet-fabric/          Fabric gameplay mod
pet-velocity/        Velocity presence/transfer integration
pet-service/         central HTTP/service, AI, jobs, Stripe, persistence
website/             existing site changes only
db/migrations/       authoritative schema migrations
deploy/systemd/      service/config templates without secrets
```

Do not force all modules into one repository if the current architecture separates them.

---

## 21. Granular implementation checklist

### Phase A — Discovery and design verification

- [ ] Confirm exact server/mod versions and mappings.
- [ ] Confirm whether `generic.scale`/mapped scale attribute is available and test it on cat/wolf hitboxes.
- [ ] Enumerate cat and wolf variant registry APIs for the target version.
- [ ] Verify exact Text Display APIs and fixed-orientation yaw convention.
- [ ] Verify existing villager UI client/server message flow.
- [ ] Inventory every backend-switch mechanism.
- [ ] Decide which existing shared protocol module owns new pet payloads.
- [ ] Confirm the Raspberry Pi MariaDB `minecraft` schema, current credentials mechanism, schema tool, backup mechanism, and connection limits.
- [ ] Confirm the pet service's chosen host can securely reach that MariaDB instance without exposing it publicly.
- [ ] Confirm existing Stripe Checkout/webhook/customer portal code.
- [ ] Write an implementation note containing discoveries and deviations before large edits.

### Phase B — Central persistence and state machine

- [ ] Add migrations for pets, traits, mood, sleep, events, long-term memory, audits, subscriptions, webhooks, recalls, usage, jobs, and account linking as applicable.
- [ ] Add schema constraints and indexes.
- [ ] Implement pet repository/service interfaces.
- [ ] Implement one-pet-per-owner transactional adoption.
- [ ] Implement bounded appearance randomization and exact persistence.
- [ ] Implement `HELD <-> PLACED` and `PLACED -> TRANSFERRING -> PLACED/HELD` CAS transitions.
- [ ] Implement entity UUID/revision validation.
- [ ] Implement stale entity decisions.
- [ ] Implement idempotency middleware/storage.
- [ ] Implement recall calendar-period transaction.
- [ ] Unit-test all transitions and races.

### Phase C — Fabric physical pet MVP

- [ ] Identify/mark pet cats and wolves without affecting ordinary mobs.
- [ ] Persist pet identity metadata on entities.
- [ ] Spawn exact cat/wolf variant and exact scale.
- [ ] Apply invulnerability.
- [ ] Disable attack/target/breed/tame/vanilla teleport behavior.
- [ ] Add owner-only right-click interception.
- [ ] Add custom follow goal with adaptive speed.
- [ ] Add stuck detection and path recalculation.
- [ ] Implement safe placement.
- [ ] Implement proximity pickup.
- [ ] Implement entity-load reconciliation.
- [ ] Implement stale entity self-discard.
- [ ] Handle server shutdown/restart and unloaded chunks.
- [ ] Verify no forced chunk loading.
- [ ] Verify physical features work while subscription inactive.

### Phase D — Commands and compass

- [ ] Register `/pet` root/status help.
- [ ] Implement adoption command/UI with active-subscription check.
- [ ] Implement `/pet place`.
- [ ] Implement `/pet pickup`.
- [ ] Implement `/pet recall` with UTC monthly period and clear feedback.
- [ ] Implement `/pet compass` issue/replace.
- [ ] Implement same-server/dimension pointing.
- [ ] Implement other-server/dimension/held status.
- [ ] Enforce owner-bound inventory behavior and duplicate cleanup.
- [ ] Implement permissions.
- [ ] Implement admin inspect/recover/reconcile operations.

### Phase E — Velocity presence and transfers

- [ ] Add network login/logout presence reporting.
- [ ] Ensure backend switches do not count as logout.
- [ ] Hook existing portal transfer before source disconnect.
- [ ] Check authoritative pet entity and carry radius.
- [ ] Perform source CAS `PLACED -> TRANSFERRING` and discard the source entity.
- [ ] Persist transfer ID, source identity, final destination, start time, and expiry.
- [ ] Do not spawn in waiting lobby by default.
- [ ] Claim the matching transfer ID on the final backend and place exactly once.
- [ ] Expire incomplete transfers safely to `HELD`.
- [ ] Handle transfer failure, proxy restart, destination offline, and player disconnect.
- [ ] Verify internal admission bypass remains unchanged.
- [ ] Verify pet left behind remains discoverable by compass.

### Phase F — Conversation UI and speech display

- [ ] Reuse/adapt existing villager text input UI.
- [ ] Bind interaction session to owner UUID, pet ID, backend, and expiration.
- [ ] Validate distance/state on message submission.
- [ ] Add inactive-subscription and sleeping feedback.
- [ ] Add async service request with timeout/correlation ID.
- [ ] Create one Text Display per active reply.
- [ ] Set position above scaled mob.
- [ ] Set fixed orientation to pet facing direction and visually confirm yaw.
- [ ] Update display position/orientation while visible.
- [ ] Sanitize, wrap, cap, and time reply text.
- [ ] Remove display on timeout/pickup/unload/shutdown/new reply.
- [ ] Test nearby-player visibility.

### Phase G — AI dialogue and short-term memory

- [ ] Add configurable model client abstraction.
- [ ] Add strict structured output schema.
- [ ] Implement moderation/safety preprocessing.
- [ ] Implement prompt builder with explicit token budgets/drop order.
- [ ] Include identity, traits, mood, relationship summary, eligible short-term events, bounded long-term recall, recent turns, and current input.
- [ ] Do not send full history.
- [ ] Implement cooldown, daily cap, concurrency lock, global cost cap, and circuit breaker.
- [ ] Parse/sanitize reply.
- [ ] Validate importance and proposed deltas.
- [ ] Persist short-term event and usage.
- [ ] Apply bounded/audited deltas.
- [ ] Display result only on valid current server/session.
- [ ] Mock model calls in automated tests.

### Phase H — Sleep scheduler and consolidation

- [ ] Implement Velocity-backed owner network presence.
- [ ] Implement 30-minute logout sleep trigger.
- [ ] Implement 23-hour forced-awake deadline.
- [ ] Implement one-hour sleep independent of physical placement.
- [ ] Prevent repeated sleep cycles during one continuous absence.
- [ ] Disable movement/chat while sleeping.
- [ ] Permit pickup while sleeping without timer reset.
- [ ] Persist restart-safe sleep timers.
- [ ] Create idempotent consolidation jobs.
- [ ] Select daily candidates deterministically.
- [ ] Skip empty/unsubscribed consolidation calls.
- [ ] Generate strict long-term memory cards and relationship summary.
- [ ] Validate/commit cards and bounded trait deltas transactionally.
- [ ] Mark/prune short-term rows according to retention policy.
- [ ] Test provider failure without extending sleep.

### Phase I — Vector retrieval

- [ ] Select pgvector/Qdrant/other after DB discovery.
- [ ] Add vector repository abstraction.
- [ ] Embed only long-term memory cards.
- [ ] Add idempotent embedding jobs and retry state.
- [ ] Scope every query by pet ID.
- [ ] Add metadata filters and similarity threshold.
- [ ] Retrieve at most the configured small memory set.
- [ ] Do not add per-message LLM query rewriting.
- [ ] Degrade gracefully if vector service is unavailable.
- [ ] Implement full reindex command from relational rows.

### Phase J — Stripe and website

- [ ] Inspect/reuse current Stripe code.
- [ ] Implement secure UUID account linking if missing.
- [ ] Configure subscription Price ID.
- [ ] Create hosted Checkout Session in subscription mode.
- [ ] Add customer portal if current site supports it.
- [ ] Verify webhook signatures.
- [ ] Store/process webhook events idempotently.
- [ ] Handle lifecycle events and out-of-order updates.
- [ ] Derive AI access without deleting pet data.
- [ ] Test cancel-at-period-end, payment failure/grace, renewal, and reactivation.
- [ ] Use Stripe test mode and fixtures for automated tests.

### Phase K — Security, operations, and deployment

- [ ] Add service authentication and firewall rules.
- [ ] Move all secrets to approved configuration.
- [ ] Add structured logs without raw secrets/conversation dumping.
- [ ] Add metrics and health/readiness endpoints.
- [ ] Add database backup coverage and restore instructions.
- [ ] Add vector reindex instructions.
- [ ] Add systemd unit with restart, memory, user, working directory, and environment configuration.
- [ ] Ensure AI service failure cannot crash Minecraft servers.
- [ ] Add global AI-disable switch preserving physical pets.
- [ ] Document deployment/rollback per component.
- [ ] Build and deploy to a staging backend before all six servers.

---

## 22. Test plan and acceptance criteria

### 22.1 Appearance tests

- [ ] Creating a cat chooses a valid cat variant and in-bounds scale.
- [ ] Creating a dog chooses a valid wolf variant and in-bounds scale.
- [ ] Repeated pickup/place preserves exact variant and size.
- [ ] Server restart preserves exact variant and size.
- [ ] Backend transfer preserves exact variant and size.
- [ ] Invalid/removed variant follows deterministic repair policy.

### 22.2 Ownership and duplicate tests

- [ ] Concurrent adoption requests create one pet.
- [ ] Concurrent `/pet place` requests create one representation.
- [ ] Player cannot interact with/pick up another owner's pet.
- [ ] Stale source entity discards after remote recall.
- [ ] Saved old entity loading after placement elsewhere discards.
- [ ] Service/backend restart never creates two authoritative entities.
- [ ] Admin entity deletion does not delete pet identity.

### 22.3 Movement tests

- [ ] Pet follows owner at near/medium/far distances.
- [ ] Pet accelerates enough to avoid routine separation.
- [ ] No vanilla teleport occurs.
- [ ] Pet does not attack or assist combat.
- [ ] Pet does not breed/tame/change owner.
- [ ] Pet recalculates after ordinary path blockage.
- [ ] No chunk-force-loading leak.
- [ ] Ordinary cats/wolves remain unchanged.

### 22.4 Placement/transfer/recall tests

- [ ] Pickup fails beyond radius.
- [ ] Pickup works while sleeping.
- [ ] Place fails if already placed anywhere.
- [ ] Near pet auto-carries through backend transfer.
- [ ] Far pet remains on source.
- [ ] Manually held pet stays held through transfer.
- [ ] Waiting lobby never accidentally materializes pet.
- [ ] Source state becomes `TRANSFERRING` before source entity discard.
- [ ] Final backend can complete only the matching transfer ID once.
- [ ] Failed destination placement or transfer expiry leaves pet held.
- [ ] One monthly recall succeeds; second fails with next date.
- [ ] Failed recall does not consume entitlement.
- [ ] Recall from offline backend cannot later duplicate.

### 22.5 Compass tests

- [ ] Correct direction on same backend/dimension.
- [ ] Correct server name on different backend.
- [ ] Correct dimension/held status.
- [ ] Dropped/container-transferred item is blocked/removed.
- [ ] Duplicate compass is removed.
- [ ] Full inventory produces clear failure.
- [ ] Works after subscription cancellation.

### 22.6 Sleep tests with fake clock

- [ ] Whole-network logout starts 30-minute timer.
- [ ] Backend switch does not start logout sleep.
- [ ] Sleep starts at 30 minutes absent.
- [ ] Forced sleep starts at 23 hours awake.
- [ ] Sleep ends exactly one hour later.
- [ ] Owner returning early does not cancel sleep.
- [ ] Pickup/place does not reset sleep.
- [ ] Long offline absence triggers only one sleep/consolidation cycle.
- [ ] Restart restores due timers without duplicate consolidation.
- [ ] AI failure does not extend sleep.

### 22.7 Conversation/memory tests

- [ ] Right-click opens existing/adapted text UI.
- [ ] No AI call occurs until submission.
- [ ] Sleeping and inactive pets do not invoke AI.
- [ ] Active subscription produces one bounded reply.
- [ ] Text Display faces pet direction, follows it, and expires.
- [ ] Malformed model JSON is rejected safely.
- [ ] Low/medium/high expirations are correct.
- [ ] Prompt builder stays below hard token cap.
- [ ] Cross-pet memory retrieval is impossible.
- [ ] Empty day skips consolidation.
- [ ] Meaningful day creates bounded long-term cards.
- [ ] Trait deltas obey per-event/daily/final bounds.
- [ ] Logout/inactivity never lowers relationship traits.
- [ ] Vector outage still permits bounded conversation without long-term recall.

### 22.8 Stripe tests

- [ ] Spoofed success redirect grants nothing.
- [ ] Valid signed webhook updates subscription.
- [ ] Duplicate webhook is idempotent.
- [ ] Out-of-order event does not regress newer status.
- [ ] Cancel-at-period-end retains access until period end.
- [ ] Cancellation disables only AI and keeps all pet data/functions.
- [ ] Renewal restores AI immediately after trusted update.
- [ ] UUID mapping survives username change.

### 22.9 Load/cost tests

- [ ] Simulate expected concurrent players and chat requests.
- [ ] Server tick time remains unaffected by provider latency.
- [ ] Database pool does not exhaust under pet heartbeats/status reads.
- [ ] Coordinate/compass behavior does not generate excessive writes.
- [ ] Global concurrency and spend caps work.
- [ ] Measure real average prompt/output sizes and estimated cost per active subscriber.
- [ ] Use results to set subscription price and final message quota.

### 22.10 Final release acceptance

Release only when all are true:

- one pet cannot duplicate across servers/restarts/recalls;
- exact appearance persists;
- no combat/pay-to-win behavior exists;
- physical pet remains after subscription cancellation;
- AI functions stop and resume correctly with subscription state;
- sleep and consolidation are restart-safe;
- prompts stay bounded;
- long-term memories are pet-scoped and authoritative in SQL;
- OpenAI, vector, Stripe, and pet-service failures do not crash gameplay;
- existing admission queue, offline waiting, portal transfers, and Cultivation server remain unaffected.

---

## 23. Explicitly deferred features

Do not add these during the initial implementation unless requested:

- pet death/resurrection;
- food, hunger, thirst, or offline decay;
- combat assistance;
- stat boosts or pay-to-win rewards;
- multiple pets per player;
- breeding/trading/selling pets;
- voice/audio;
- elaborate emote system;
- custom models/resource-pack dependency;
- arbitrary mob allowlist;
- spontaneous high-frequency AI commentary;
- per-message LLM query rewriting for RAG;
- reliable two-block jumping/custom parkour navigation;
- player marketplace for personalities;
- pet memories shared across owners;
- integration with the separate Cultivation server.

---

## 24. Coding standards and handoff expectations

- Follow existing project language/style/package conventions.
- Prefer small interfaces and explicit state transitions over broad abstractions.
- Avoid boilerplate frameworks that materially increase server footprint without benefit.
- Use migrations; never mutate production schema manually from application startup unless that is already the project's established migration strategy.
- Make all external integrations replaceable in tests.
- Use fake clocks, fake model clients, fake Stripe events, and test repositories.
- Use conventional commits when committing, grouped by coherent phases.
- Preserve unrelated user changes in dirty working trees.
- Do not deploy directly to all production backends before staging and acceptance tests.
- Document every new service, port, firewall rule, environment variable, command, permission, migration, and rollback step.
- At the end of each phase, report:
  - files/modules changed;
  - tests run and results;
  - migrations/config/deployment actions still needed;
  - known limitations;
  - next phase.

---

## 25. First actions for the IDE coding AI

1. Read this document completely.
2. Inspect the current repositories and version/configuration files.
3. Find and summarize the existing villager chat UI, shared Fabric portal protocol, Velocity transfer path, Stripe integration, database engine, and deployment layout.
4. Produce a short implementation map showing which existing modules will change and which new modules are actually necessary.
5. Call out any conflict between this specification and the current code/version with concrete evidence.
6. Begin with database/state-machine tests and the physical cat/dog MVP—not AI calls or Stripe UI.
7. Do not change the working admission architecture or Cultivation server.

The implementation order should be: authority and duplicate safety first; physical pet lifecycle second; transfer/compass third; conversation UI fourth; bounded AI/memory fifth; Stripe lifecycle sixth; operational hardening last.
