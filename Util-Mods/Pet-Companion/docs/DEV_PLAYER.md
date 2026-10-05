# Dev Player: isolated Pet Companion diagnostics

The Dev Player is an operator-only Java CLI on `raspberrypi`. It calls the current
service classes for dialogue, MariaDB context/state, OpenAI embedding and model
calls, Qdrant retrieval, planning, sleep, and consolidation. It does **not** expose
a development HTTP route or change production authorization, ownership, or
subscription checks. The synthetic owner is
`1ec1f4a8-8c9b-4e41-b978-3c84f658eabc`; cat Mica is
`9f8eaf42-5a96-4dc4-a57d-a4e60e33ed19`. These identities persist until
`reset` is explicitly run. For independent investigations, set a named session:

```sh
# raspberrypi shell, as silver; keep this value for the entire conversation.
export DEV_PLAYER_SESSION=my-conversation-20261001
bash /home/silver/pet-companion/dev-player/dev_player.sh create
bash /home/silver/pet-companion/dev-player/dev_player.sh say 'Hello.'
```

Names must match `[a-z0-9][a-z0-9_-]{0,63}`. Each name derives stable synthetic
owner/pet/entity UUIDs and its own clock/report directory. Omitting the variable
uses the original default identities above. `create` preserves existing history.

## Storage boundary

**Named-session worker isolation:** current source scopes sleep, embedding, and
consolidation work to the selected pet. The launcher uses installed jars by
default. To test candidate jars, set `DEV_PLAYER_CANDIDATE=1`; it requires both
candidate jars and exits rather than falling back if either is missing:

```sh
# raspberrypi shell, as silver; verify both candidate jars before use.
export DEV_PLAYER_CANDIDATE=1
export DEV_PLAYER_SESSION=my-isolated-check
bash /home/silver/pet-companion/dev-player/dev_player.sh create
bash /home/silver/pet-companion/dev-player/dev_player.sh say 'Hello.'
```

Check candidate hashes before running them. Unset `DEV_PLAYER_CANDIDATE` to use
the installed service classes again.

- MariaDB: `pet_companion_dev_player`, account `pet_dev_player@127.0.0.1`
  with grants only on that schema. It uses the same V001–V016 schema as the
  production service. Credentials are mode 0600 in
  `/home/silver/.config/pet-companion/dev-player.env`.
- Qdrant: `pet_companion_dev_player_memories`, separate from
  `aipets_memories`. Qdrant has one server API key, so the CLI enforces the
  exact collection name and local endpoint before constructing any adapter.
- CLI state and Markdown transcript:
  `/home/silver/.local/state/pet-dev-player/`. The clock is persisted in
  `clock.txt`; advancing it changes only synthetic event times. Named sessions
  live under a same-named subdirectory.
- The launcher imports the configured model key and model names from the
  protected service environment, then unsets production SQL, bearer, Stripe,
  and account-link credentials before starting the CLI. The CLI refuses any
  SQL URL, account, or vector collection outside its exact test identifiers.

Run on `raspberrypi` as `silver`:

```sh
bash /home/silver/pet-companion/dev-player/dev_player.sh create
bash /home/silver/pet-companion/dev-player/dev_player.sh say 'I prefer the hill path.'
bash /home/silver/pet-companion/dev-player/dev_player.sh \
  say 'Can you see me?' --scene-json '{"proximity":"NEAR","ownerVisible":true,"ownerActivity":"STILL"}'
bash /home/silver/pet-companion/dev-player/dev_player.sh \
  observe ARRIVED_TOGETHER 'We crossed a spruce bridge together in the rain.'
bash /home/silver/pet-companion/dev-player/dev_player.sh advance PT45M
bash /home/silver/pet-companion/dev-player/dev_player.sh sleep
bash /home/silver/pet-companion/dev-player/dev_player.sh consolidate
bash /home/silver/pet-companion/dev-player/dev_player.sh wake
bash /home/silver/pet-companion/dev-player/dev_player.sh inspect
bash /home/silver/pet-companion/dev-player/dev_player.sh run-scenario
bash /home/silver/pet-companion/dev-player/dev_player.sh bench 16
bash /home/silver/pet-companion/dev-player/dev_player.sh bench 28
bash /home/silver/pet-companion/dev-player/dev_player.sh diagnose-provider
```

`traits` accepts eight integers in this order: curiosity, boldness,
playfulness, expressiveness, independence, attachment, trust, security. For
example, `traits 70 30 40 35 80 30 45 40` makes an independent, reserved cat.
`seed-owner-interpretation INTENT EMOTION CONFIDENCE` adds a labeled synthetic
low-confidence `pet_mood` fixture tied to the latest Dev Player dialogue event;
it is only for checking temporary prompt context and does not create a memory,
relationship event, embedding, or vector point. Corrections and the 3-interaction
/ 24-hour expiry still use the normal service behavior.
`say TEXT --vector-offline` simulates a Qdrant search outage for that call and
shows whether relational fallback supplies a relevant card. `reset` deletes
**only the selected pet's** isolated SQL rows and Qdrant points, resets its
report/clock, and recreates the same owner/pet. It does not clear other sessions
or migration history. Reset is never implicit in ordinary conversation commands.
Run `run-scenario` after reset for a fresh chronological
transcript covering ordinary chat, witnessed bridge, owner preference and
correction, vector recall and fallback, unfinished activity, quiet local
intention, absence, sleep, consolidation, waking, and unrelated recall.

The CLI `observe` event is **simulated**: it writes a bounded witnessed event
in the isolated schema and invokes the real planner. No Minecraft entity
produced it. `say` uses a synthetic scene with unknown fields unless the
caller supplies a valid `PetSceneSnapshot` JSON. It does not prove floating
text, attention sound, visible movement, or actual player-visible latency.
The existing Fabric GameTests exercise those local pathways separately.

After preserving the report, remove only a named run's synthetic state:

```sh
# raspberrypi shell, with DEV_PLAYER_SESSION still set to this run.
bash /home/silver/pet-companion/dev-player/dev_player.sh cleanup
```

`cleanup` refuses the default identity, filters vectors by this pet ID, and
detaches only that test pet's self-referencing supersession links, then deletes
SQL children before parents with foreign keys enabled. Reports and the
clock remain available; other Dev Player pets and production resources are not
modified. Do not use a global schema/collection reset to clean up one run.

## Spatial recall fixtures

`observe TYPE SUMMARY --location-json JSON --no-decision` stores a synthetic
witnessed place in the existing event metadata without invoking the planner.
Omitting the location leaves it unknown. Use only server-event fixtures; this
command does not establish that Minecraft actually witnessed the event.

```sh
# raspberrypi, silver; use one named isolated session throughout.
export DEV_PLAYER_SESSION=spatial-check-20261001
bash /home/silver/pet-companion/dev-player/dev_player.sh observe ARRIVED_TOGETHER \
  'We crossed a spruce bridge together in the rain.' \
  --location-json '{"server":"dev_player_simulated","world":"test-save","dimension":"minecraft:overworld","x":100,"y":64,"z":-20}' \
  --no-decision
bash /home/silver/pet-companion/dev-player/dev_player.sh say 'Do you recall the spruce bridge?' \
  --scene-json '{"location":{"server":"dev_player_simulated","world":"test-save","dimension":"minecraft:overworld","x":108,"y":64,"z":-20}}'
```

The location object requires all six fields and finite coordinates; missing or
historical partial stored locations are unknown. The scene's position is a
current recall cue, never the location of something described in chat. Sleep
copies only cited witnessed locations into existing `location_tags`; owner reports
and model-suggested tags cannot provide coordinates. SQL retains source episodes.

Recall first admits semantically/lexically relevant cards, then adds at most
`0.05 * max(0, 1 - distance / 128)` for the nearest cited witnessed place in the
same server/save/dimension. Distance uses XYZ; other spaces and unknown positions
receive zero boost, with no distance filter. The trace reports raw similarity,
combined rank, locations, selected prompt context and `spatial_memory_reranking`.
Only two historical positions per selected card are rendered to bound prompt
size; all cited positions (at most 30) remain in SQL metadata. Cleanup remains
scoped to the named pet.

## Luna output and evidence boundaries

The current service maps Luna's reply, conversation mode, action, attention,
affect, and relationship signal independently. It does not classify reply text,
replace it with canned conversation, enforce sentence counts, or infer an action
from words in the reply. Fabric translates Luna's action enum directly into the
existing local controller. Rendering still bounds/sanitizes floating text; local
execution checks ownership, scene freshness, range and movement safety.
Dialogue requests send context/instructions as a system message followed by the
current owner text as a separate user message. The report records both exact
messages. Recent turns remain source-labeled context; they are not proof of
world observations.

Malformed/truncated structured responses and moderation failures are still
rejected. Typed `memory_candidate` proposals cite an exact current-owner quote;
target IDs/versions must belong to the included pet context. These checks govern
persisted provenance, not conversational wording. Owner reports never become
witnessed episodes or proof of the pet's own preferences. Diagnostics retain the
historical label `Validated/displayed reply` for the mapped output.

`compare-conversation bridge_records` runs three paired, non-persisting model
comparisons and explicitly resets **only the selected isolated pet** between
repetitions. Its `deployed_answer_first` label denotes the historical answer-first
policy, not the current deployment. Do not use this command during a continuous
conversation. Compare raw replies as well as displayed replies; successful
schema decoding is not evidence of good conversation.

## Reading diagnostics

Each command appends a correlated section to `session.md`: input/scene,
before/after test-row snapshots, embedding text and timing, Qdrant point IDs
and candidate scores, selected cards and source, exact rendered model message
and schema, raw structured model content, validated/displayed text, and
monotonic stage timings. Common credential shapes are redacted. All data in
this report belongs to the synthetic pet; no production rows are read by the
CLI. The Chat Completions transport returns a complete response, so time to
first token is unavailable. The direct CLI has no HTTP response transmission
or Minecraft display timing. `service_pipeline_to_validated_reply` ends when
the dialogue core returns; `cli_total_including_embedding_job` also includes
diagnostic snapshots and explicit indexing.

Raw transcripts contain complete synthetic prompts and model responses. Keep
them outside the repository; commit only a reviewed, concise summary. The
analysis scripts accept a local transcript path:

```sh
python deploy/scripts/summarize_dev_player.py <session.md> 16
python deploy/scripts/analyze_pet_latency.py <session.md>
```

`bench 28` gives four distinct cold queries followed by 24 warm repetitions
in one process. `diagnose-provider` makes 20 isolated, non-persisting
same-model calls comparing the full request, a short prompt with the same
schema, no retrieved memories, a reply-only diagnostic schema, and low
reasoning. They do not change production pet settings, but model calls may
incur cost and their raw output should remain local.

## Installation and recovery

`deploy/scripts/setup_dev_player_storage.sh` creates the dedicated SQL schema
and account once. `deploy/scripts/migrate_dev_player.sh` applies the existing
migrations in version order to that schema only; its optional first argument
is a version for resuming a partially applied new schema. The MariaDB V004
migration uses `LONGTEXT` rather than SQL Server `NVARCHAR(MAX)`.

`deploy/scripts/deploy_dev_player_service.sh EXPECTED_SHA256` verifies the
candidate and same-directory `.next` copy, atomically replaces the service jar,
restarts only `pet-companion.service`, and checks the installed hash. It creates
no backup and does not restart Minecraft. Run
`deploy/scripts/verify_dev_player_deploy.sh` afterward to check service readiness.

For organic-companion diagnostics after V016, use `reset` only when you intend
to clear the isolated session, then run `run-scenario`. Dialogue calls,
simulated observations, sleep, consolidation, wake, and later recall share the
same stable Dev Player identity. `inspect` and `session.md` include typed
episode provenance, low-confidence model candidates, affect/interpretation,
self-model versions, open-thread states/versions, and supersession links.
`observe` remains a simulated service event, not a Fabric-produced event.

Dialogue provenance, affect, self-model, and open-thread behavior are described
in `docs/EXPERIENCE_UPGRADE.md`. Keep any raw Dev Player session output local;
the CLI operates only on its isolated synthetic identity and schema.
