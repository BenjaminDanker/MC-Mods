package com.silver.aipets.service.subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable one-use token boundary; callers pass only keyed token digests. */
public interface AccountLinkRepository {
    boolean create(
            UUID ownerUuid,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt,
            Instant generationWindowStart,
            int maximumGenerations);

    Optional<AccountLinkTarget> findValid(String tokenHash, Instant checkedAt);

    boolean attachCheckout(
            String tokenHash, String checkoutSessionId, Instant checkoutStartedAt);
}
