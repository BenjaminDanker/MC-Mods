package com.silver.aipets.service.subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.silver.aipets.service.adoption.CheckoutLaunchClaim;

/** Durable one-use token boundary; callers pass only keyed token digests. */
public interface AccountLinkRepository {
    boolean create(
            UUID ownerUuid,
            String tokenHash,
            String checkoutAliasHash,
            Instant createdAt,
            Instant expiresAt,
            Instant generationWindowStart,
            int maximumGenerations);

    Optional<AccountLinkTarget> findValidAlias(String checkoutAliasHash, Instant checkedAt);

    boolean attachCheckout(
            String tokenHash, String checkoutSessionId, Instant checkoutStartedAt);

    default CheckoutLaunchClaim claimCheckoutStart(
            String tokenHash, Instant now, Instant staleClaimBefore) {
        return CheckoutLaunchClaim.CLAIMED;
    }

    default void releaseCheckoutStart(String tokenHash, Instant claimedAt) {
    }
}
