package com.silver.aipets.service.subscription;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface SubscriptionCustomerLookup {
    Optional<String> findCustomerId(UUID ownerUuid);
}
