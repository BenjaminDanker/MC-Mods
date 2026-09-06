package com.silver.aipets.velocity;

import com.silver.aipets.common.transport.PetPresenceWireRequest;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface PresenceGateway {
    CompletionStage<Void> publish(PetPresenceWireRequest request);
}
