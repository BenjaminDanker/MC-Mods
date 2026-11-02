ALTER TABLE account_link_tokens
    ADD COLUMN checkout_alias_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER link_token_hash,
    ADD UNIQUE KEY uq_account_link_checkout_alias (checkout_alias_hash),
    ADD CONSTRAINT chk_account_link_alias_hash CHECK (
        checkout_alias_hash IS NULL OR checkout_alias_hash REGEXP '^[0-9a-f]{64}$'
    );
