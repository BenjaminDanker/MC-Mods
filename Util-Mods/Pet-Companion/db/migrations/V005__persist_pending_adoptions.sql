-- Durable subscription-gated adoption intent. A checkout-started intent remains
-- tied to its exact account-link hash and Checkout Session; late payment events
-- can update subscription state without reviving an expired intent.
CREATE TABLE pending_adoptions (
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    intent_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    species ENUM('CAT', 'DOG') NOT NULL,
    pet_name VARCHAR(64) NOT NULL,
    state ENUM('PRE_CHECKOUT', 'CHECKOUT_STARTED', 'COMPLETED', 'EXPIRED', 'CANCELLED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    checkout_started_at DATETIME(6) NULL,
    hard_expires_at DATETIME(6) NULL,
    account_link_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    stripe_checkout_session_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    checkout_launch_claimed_at DATETIME(6) NULL,
    checkout_completed_at DATETIME(6) NULL,
    completed_pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    completed_at DATETIME(6) NULL,
    notification_pending BOOLEAN NOT NULL DEFAULT FALSE,
    notification_acknowledged_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (owner_uuid),
    UNIQUE KEY uq_pending_adoptions_intent (intent_id),
    UNIQUE KEY uq_pending_adoptions_link_hash (account_link_hash),
    UNIQUE KEY uq_pending_adoptions_checkout_session (stripe_checkout_session_id),
    KEY ix_pending_adoptions_state_expiry (state, expires_at, hard_expires_at),
    KEY ix_pending_adoptions_notification (notification_pending, owner_uuid),
    CONSTRAINT fk_pending_adoptions_subscription
        FOREIGN KEY (owner_uuid) REFERENCES subscriptions (owner_uuid),
    CONSTRAINT fk_pending_adoptions_link
        FOREIGN KEY (account_link_hash) REFERENCES account_link_tokens (link_token_hash),
    CONSTRAINT fk_pending_adoptions_pet
        FOREIGN KEY (completed_pet_id) REFERENCES pets (pet_id),
    CONSTRAINT chk_pending_adoptions_expiry CHECK (expires_at >= created_at),
    CONSTRAINT chk_pending_adoptions_checkout_pair CHECK (
        (checkout_started_at IS NULL AND hard_expires_at IS NULL
            AND stripe_checkout_session_id IS NULL)
        OR (checkout_started_at IS NOT NULL AND hard_expires_at IS NOT NULL
            AND stripe_checkout_session_id IS NOT NULL)
    ),
    CONSTRAINT chk_pending_adoptions_completion_pair CHECK (
        (completed_pet_id IS NULL AND completed_at IS NULL)
        OR (completed_pet_id IS NOT NULL AND completed_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
