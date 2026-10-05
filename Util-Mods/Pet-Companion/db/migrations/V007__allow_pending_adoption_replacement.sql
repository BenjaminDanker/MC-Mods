-- Keep superseded intents and their Stripe/account-link associations for webhook accounting.
-- intent_id is the durable identity; owner_uuid now identifies the current intent by newest created_at.
ALTER TABLE pending_adoptions
    DROP PRIMARY KEY,
    DROP INDEX uq_pending_adoptions_intent,
    ADD PRIMARY KEY (intent_id),
    ADD KEY ix_pending_adoptions_owner_created (owner_uuid, created_at, intent_id);
