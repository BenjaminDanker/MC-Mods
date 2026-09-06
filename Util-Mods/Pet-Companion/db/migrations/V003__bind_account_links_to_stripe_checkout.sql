-- Checkout is created only after the opaque account-link token is resolved. The
-- session ID is persisted before redirect so the signed webhook can atomically
-- prove which server-issued link it is consuming, even if payment finishes after
-- the short link-opening TTL.
ALTER TABLE account_link_tokens
    ADD COLUMN stripe_checkout_session_id VARCHAR(255)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER owner_uuid,
    ADD COLUMN checkout_started_at DATETIME(6) NULL AFTER stripe_checkout_session_id,
    ADD UNIQUE KEY uq_account_link_checkout_session (stripe_checkout_session_id),
    ADD KEY ix_account_link_checkout_started (checkout_started_at),
    ADD CONSTRAINT chk_account_link_checkout_pair CHECK (
        (stripe_checkout_session_id IS NULL AND checkout_started_at IS NULL)
        OR (stripe_checkout_session_id IS NOT NULL AND checkout_started_at IS NOT NULL)
    );
