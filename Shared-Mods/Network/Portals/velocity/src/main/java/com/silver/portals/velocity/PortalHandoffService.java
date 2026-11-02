package com.silver.portals.velocity;

import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Coordinates durable, target-bound and single-consumption portal handoffs. */
public final class PortalHandoffService {
    private final PortalTransferStore store;
    private final Logger log;

    public PortalHandoffService(PortalTransferStore store, Logger log) {
        this.store = Objects.requireNonNull(store, "store");
        this.log = Objects.requireNonNull(log, "log");
    }

    public CompletableFuture<PortalTransfer> createTransfer(
            UUID playerId, String sourceServer, String targetServer, String arrivalPortal) {
        return store.createTransfer(playerId, sourceServer, targetServer, arrivalPortal);
    }

    public Optional<PortalTransfer> pendingTransfer(UUID playerId) {
        return store.pendingTransfer(playerId);
    }

    public CompletableFuture<Optional<PortalTransfer>> claimForTarget(UUID playerId, String targetServer) {
        return store.claimForTarget(playerId, targetServer);
    }

    public CompletableFuture<Void> connectionFailed(UUID playerId, PortalTransfer transfer) {
        return store.connectionFailed(playerId, transfer);
    }

    public CompletableFuture<Void> completeConnectedTransfer(UUID playerId, String server) {
        return store.completeConnectedTransfer(playerId, server);
    }

    public CompletableFuture<Void> cancelActiveTransfer(UUID playerId) {
        return store.cancelActiveTransfer(playerId);
    }

    public byte[] consumeResponsePayload(UUID playerId, String requestingServer) {
        Optional<PortalTransfer> transfer = store.consumeHandoff(playerId, requestingServer)
                .filter(value -> !value.arrivalPortal().isBlank());
        transfer.ifPresent(value -> log.info(
                "[PortalHandoffService] delivering transfer={} arrival='{}' player={} target={}",
                value.transferId(), value.arrivalPortal(), playerId, value.targetServer()));
        Optional<PortalHandoffPayloadCodec.PortalHandoffResponse> response = transfer.map(value ->
                new PortalHandoffPayloadCodec.PortalHandoffResponse(
                        value.transferId(), playerId, value.arrivalPortal(), value.expiresAt().toEpochMilli()));
        return PortalHandoffPayloadCodec.encode(response);
    }
}
