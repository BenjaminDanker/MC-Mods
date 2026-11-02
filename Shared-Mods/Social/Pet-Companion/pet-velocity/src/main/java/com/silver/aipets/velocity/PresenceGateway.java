package com.silver.aipets.velocity;

import com.silver.aipets.common.transport.PetPresenceWireRequest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface PresenceGateway {
    CompletionStage<Void> publish(PetPresenceWireRequest request);

    default CompletionStage<Void> reconcile(Set<UUID> onlineOwnerUuids) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("presence reconciliation is unavailable"));
    }
}
