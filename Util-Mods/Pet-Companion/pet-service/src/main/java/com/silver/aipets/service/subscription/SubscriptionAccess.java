package com.silver.aipets.service.subscription;

import java.util.UUID;

/** Trusted subscription-state boundary; usernames are deliberately absent. */
@FunctionalInterface
public interface SubscriptionAccess {
    boolean canAdopt(UUID ownerUuid);

    /** Returns the lifecycle projection when the backing implementation can provide it. */
    default SubscriptionAccessDetails details(UUID ownerUuid) {
        return new SubscriptionAccessDetails(canAdopt(ownerUuid), "UNKNOWN", false, null);
    }
}
