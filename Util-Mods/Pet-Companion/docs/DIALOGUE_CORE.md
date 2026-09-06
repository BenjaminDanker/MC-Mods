# Bounded dialogue core

The service-side dialogue pipeline is implemented but is not yet wired to a production model or
the Fabric text-entry/speech-display flow.

Its required ordering is fixed:

1. Verify authoritative owner, AI-access, and sleep state before any model call.
2. Normalize/moderate player input and reject unsafe/oversized text.
3. Atomically acquire per-pet/global admission after cooldown, daily, spend, and circuit checks.
4. Build a model-tokenizer-counted prompt with fixed core rules and explicit drop order.
5. Make one timeout-bounded structured-output request.
6. Strictly reject missing/extra fields, noninteger deltas, or invalid importance.
7. Sanitize/cap the reply and treat all deltas as proposals.
8. In one MariaDB transaction, lock state, reapply per-event/daily/final clamps, insert the event
   and usage row, update traits/mood, and insert trait audits.

The provider adapter must implement `DialogueModelClient` and supply the configured model's real
`DialogueTokenCounter`; heuristic character/word counters are not acceptable in production.
Provider errors and usage rows retain only bounded categories/metrics, never full prompts,
authorization data, or response bodies.

Current defaults are a 500-character owner message, 4,000 hard input tokens, four/700-token recent
turns, three/600-token long-term cards, 1,200 short-term tokens, five-second cooldown, 100 successful
replies per owner per UTC day, one active request per pet, bounded global concurrency/spend, and a
failure circuit breaker. Final cost caps and model choices require measured staging data.

