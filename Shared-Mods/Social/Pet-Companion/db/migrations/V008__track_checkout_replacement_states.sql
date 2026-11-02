-- A replacement can be waiting for Stripe to confirm closure, or for entitlement
-- from a completed superseded checkout. Neither state is payable through the alias.
ALTER TABLE pending_adoptions
    MODIFY COLUMN state ENUM(
        'PRE_CHECKOUT', 'CHECKOUT_STARTED', 'REPLACEMENT_PENDING',
        'WAITING_FOR_ENTITLEMENT', 'COMPLETED', 'EXPIRED', 'CANCELLED'
    ) NOT NULL;
