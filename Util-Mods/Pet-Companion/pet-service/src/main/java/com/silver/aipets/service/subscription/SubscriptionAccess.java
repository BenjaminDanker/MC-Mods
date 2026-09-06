package com.silver.aipets.service.subscription;

import java.util.UUID;

/** Trusted subscription-state boundary; usernames are deliberately absent. */
@FunctionalInterface
public interface SubscriptionAccess {
    boolean canAdopt(UUID ownerUuid);
}
