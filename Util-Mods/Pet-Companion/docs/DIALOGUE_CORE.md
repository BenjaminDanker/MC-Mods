# Bounded dialogue core

The service-side dialogue pipeline is wired to an opt-in OpenAI adapter and the provider-neutral
authenticated `/v1/dialogue` transport. The selected defaults are `gpt-6-luna` with
`reasoning_effort=none` for structured replies and `omni-moderation-latest` for input safety. A JDBC context loader binds owner and pet
identities, reads authoritative sleep/subscription state, and supplies bounded event and
pet-scoped relational/vector-memory context before the model call. `PET_DIALOGUE_ENABLED=false` remains
the safe default until paid provider traffic is intentionally enabled.

Fabric backends select the transport independently with `conversation.mode`: `staging` is a
deterministic local reply, `service` uses the authenticated central `/v1/dialogue` route, and
`disabled` fails closed with a bounded unavailable response. Service mode is intentionally not
enabled by default on any backend.

Sleep consolidation is independently gated by `PET_CONSOLIDATION_ENABLED` and uses its own
configured model. It is disabled by default, so enabling dialogue or embeddings never silently
starts sleep consolidation calls. Only sleep cycles with meaningful pending events enqueue
a job; brief sitting/rest actions and empty sleep cycles make no model call.

When disabled, the runtime returns an authenticated, bounded `DENIED` result through
`UnavailableDialogueResponder` rather than exposing an unhandled route or silently accepting text.

Its required ordering is fixed:

1. Verify authoritative owner, AI-access, and sleep state before any model call.
2. Normalize/moderate player input and reject unsafe/oversized text.
3. Atomically acquire per-pet/global admission after cooldown, period budget, spend, and circuit checks.
4. Build a model-tokenizer-counted prompt with fixed core rules and explicit drop order.
5. Make one timeout-bounded, non-streaming structured-output request with
   `reasoning_effort=none` and `max_completion_tokens=2000`.
6. Require a normal `finish_reason=stop`; a token-limit finish or malformed,
   incomplete, missing-field, or extra-field response is rejected before any
   pet event, reply, memory, affect, relationship evidence, self-model evidence,
   or open-thread update is committed. The owner receives the existing bounded
   invalid-output response, with no automatic second dialogue call.
7. Sanitize/cap the reply and treat all deltas as proposals.
8. In one MariaDB transaction, lock state, reapply per-event/daily/final clamps, insert the event
   and usage row, update traits/mood/affect, add only provisional self-model evidence, and insert
   trait audits. Candidate evidence is never a stable preference by itself.

The provider adapter must implement `DialogueModelClient` and supply the configured model's real
`DialogueTokenCounter`; heuristic character/word counters are not acceptable in production.
Provider errors and usage rows retain only bounded categories/metrics, never full prompts,
authorization data, or response bodies.

The deterministic local safety checks always run first. A successful moderation response with
`flagged=true` rejects the message; a moderation transport/availability failure logs only its
bounded category and allows the locally accepted message to continue.

Current defaults are a 500-character owner message, 4,000 hard input tokens, four/700-token recent
turns, three/600-token long-term cards, 1,200 short-term tokens, five-second cooldown, one active
request per pet, bounded global concurrency/spend, and a failure circuit breaker. Production admission
uses the configured subscription-period AI budget: `PET_SUBSCRIPTION_GROSS_USD` minus the configured
Stripe payment percentage, fixed fee, and Billing percentage. Every provider-reported dialogue,
consolidation, and embedding token is priced and persisted in `ai_usage`; the authenticated billing
projection reports consumed and remaining USD for the current period. `PET_DIALOGUE_DAILY_REPLY_CAP`
is retained only as a compatibility safety ceiling and is not the production quota.

Provider diagnostics record the configured completion ceiling, finish reason,
token-limit status, request IDs, JSON validation result, and monotonic blocking
HTTP duration without logging raw dialogue.

`JdbcDialogueContextLoader` obtains recent/short-term turns through the bounded JDBC history
reader, which excludes expired or redacted event text. When Qdrant is enabled, the loader performs
bounded pet-scoped vector recall using the configured embedding model and rehydrates every result
from authoritative MariaDB rows. A locally-safe query is required before embedding; if embedding,
Qdrant, or result validation is unavailable, the loader falls back to the bounded relational card
slice. The production prompt counter uses JTokkit's `o200k_base` encoding, matching the selected
GPT-5 family tokenizer, and provider-reported input usage is checked again before output validation.

The compact response adds an explicit `conversation_mode` and a provisional
`self_model_candidate`; both are part of the same Luna call. Modes are checked
against reply shape (ASK/CLARIFY need one question; BRIEF must remain one short
sentence) and invalid combinations fall back to ANSWER. A self-model inference
stays in `pet_traits.self_model_json.pending` with INFERENCE source, bounded
confidence, interaction IDs, and UTC support days. Only three distinct
interactions across at least two UTC days can promote a candidate during sleep
consolidation. Owner statements remain owner-reported memory and cannot count as
pet preference evidence. Optimistic self-model versions reject stale writers.

Behavioral affect remains in `pet_mood`: valence decays with a 24-hour half-life,
attentional alertness with a 2-hour half-life, and social stance with a 7-day
half-life. Alertness is clamped to 0–100; existing affect labels map to bounded
service-side deltas and explicit strength 0–2. Negative social stance blocks
optional approach actions at -40 or below. Affect never rewrites the stable pet
voice, and model output cannot replace the full persisted affect state.
