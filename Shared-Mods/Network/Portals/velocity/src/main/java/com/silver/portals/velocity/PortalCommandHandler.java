package com.silver.portals.velocity;

import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Handles a verified portal request (portals:transfer): stores the transfer, then sends the player straight to the
 * target or through the waiting lobby.
 */
public class PortalCommandHandler {

    public interface Dependencies {
        CompletionStage<?> createTransfer(UUID playerId, String sourceServer, String targetServer, String arrivalPortal);
        Optional<String> currentServerName(Player player);
        Optional<HoldingConnection> resolveHoldingServer(Player player, String target, Optional<String> originServer);
        void beginStickyWait(UUID playerId, String target, Optional<String> originServer);
        void markInternalOnce(UUID playerId, String target);
        void notifyTransferFailure(Player player);
        String holdingServerName();

        /**
         * Sends the player straight to the target when it is already up (no stop in the waiting lobby). Completes with
         * true when they were moved; false means use the lobby and wait as usual.
         */
        default CompletionStage<Boolean> tryDirectConnect(Player player, String target) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    @FunctionalInterface
    public interface HoldingConnection {
        void connect();
    }

    private final Logger log;
    private final Dependencies dependencies;

    public PortalCommandHandler(Logger log, Dependencies dependencies) {
        this.log = Objects.requireNonNull(log, "log");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public boolean handleAuthorized(Player player, String targetServer, Optional<String> arrivalPortalOpt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(arrivalPortalOpt, "arrivalPortalOpt");

        UUID playerId = player.getUniqueId();
        Optional<String> originServer = dependencies.currentServerName(player);
        log.info("[Portals] portal request: origin='{}' target='{}' holding='{}'",
                originServer.orElse("<none>"), targetServer, dependencies.holdingServerName());

        Optional<HoldingConnection> holdingConnection = dependencies.resolveHoldingServer(player, targetServer, originServer);
        if (holdingConnection.isEmpty()) {
            log.error("[Portals] Holding server '{}' not found when processing a portal request for player {}",
                    dependencies.holdingServerName(), player.getUsername());
            return false;
        }

        String sourceServer = originServer.orElse("unknown");
        String arrivalPortal = arrivalPortalOpt.filter(value -> !value.isBlank()).orElse("");
        dependencies.createTransfer(playerId, sourceServer, targetServer, arrivalPortal)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        log.error("[Portals] Failed to persist portal transfer for {} -> {}",
                                player.getUsername(), targetServer, failure);
                        dependencies.notifyTransferFailure(player);
                        return;
                    }
                    dependencies.tryDirectConnect(player, targetServer).whenComplete((direct, directFailure) -> {
                        if (directFailure == null && Boolean.TRUE.equals(direct)) {
                            log.info("[Portals] {} went straight to '{}' (it was up)", player.getUsername(), targetServer);
                            return;
                        }
                        dependencies.beginStickyWait(playerId, targetServer, originServer);
                        dependencies.markInternalOnce(playerId, dependencies.holdingServerName());
                        holdingConnection.get().connect();
                    });
                });
        return true;
    }
}
