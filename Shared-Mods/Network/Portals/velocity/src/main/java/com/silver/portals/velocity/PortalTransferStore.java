package com.silver.portals.velocity;

import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Portal transfer state: MariaDB is the authority, with local read-through maps for Velocity's synchronous events.
 * All mutations are serialized on a dedicated virtual-thread executor.
 */
public class PortalTransferStore implements AutoCloseable {
    private final PortalTransferRepository repository;
    private final Logger log;
    private final Clock clock;
    private final Duration transferLifetime;
    private final ExecutorService writes = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("portals-db-", 0).factory());

    private final ConcurrentHashMap<UUID, PortalTransfer> pendingTransfers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PortalTransfer> inFlightTransfers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PortalTransfer> handoffsAvailable = new ConcurrentHashMap<>();

    public PortalTransferStore(PortalTransferRepository repository, Logger log, Clock clock, Duration transferLifetime) {
        this.repository = repository;
        this.log = log;
        this.clock = clock;
        this.transferLifetime = transferLifetime;
    }

    /** Creates the table if needed and loads the transfers that were pending when the proxy last stopped. */
    public static PortalTransferStore initialize(PortalTransferRepository repository, Logger log) throws SQLException {
        repository.ensureTable();
        PortalTransferStore store = new PortalTransferStore(repository, log, Clock.systemUTC(), Duration.ofHours(24));
        store.pendingTransfers.putAll(repository.loadPending(Instant.now()));
        log.info("[PortalTransfer] Loaded pendingTransfers={}", store.pendingTransfers.size());
        return store;
    }

    public Optional<PortalTransfer> pendingTransfer(UUID playerId) {
        PortalTransfer transfer = pendingTransfers.get(playerId);
        if (transfer == null) return Optional.empty();
        if (transfer.expiredAt(clock.instant())) {
            pendingTransfers.remove(playerId, transfer);
            return Optional.empty();
        }
        return Optional.of(transfer);
    }

    public CompletableFuture<PortalTransfer> createTransfer(UUID playerId, String sourceServer,
                                                             String targetServer, String arrivalPortal) {
        Instant now = clock.instant();
        return CompletableFuture.supplyAsync(() -> {
            try {
                PortalTransfer transfer = repository.createTransfer(playerId, sourceServer, targetServer,
                        arrivalPortal, now, now.plus(transferLifetime));
                pendingTransfers.put(playerId, transfer);
                inFlightTransfers.remove(playerId);
                handoffsAvailable.remove(playerId);
                log.info("[PortalTransfer] created transfer={} player={} source={} target={} arrival='{}'",
                        transfer.transferId(), playerId, sourceServer, targetServer, arrivalPortal);
                return transfer;
            } catch (SQLException failure) {
                throw new IllegalStateException("Failed to create portal transfer", failure);
            }
        }, writes);
    }

    public CompletableFuture<Optional<PortalTransfer>> claimForTarget(UUID playerId, String targetServer) {
        PortalTransfer pending = pendingTransfer(playerId).orElse(null);
        if (pending == null || !pending.targetServer().equalsIgnoreCase(targetServer)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<PortalTransfer> claimed = repository.claimPending(playerId, targetServer, clock.instant());
                claimed.ifPresent(transfer -> {
                    pendingTransfers.remove(playerId);
                    inFlightTransfers.put(playerId, transfer);
                    handoffsAvailable.put(playerId, transfer);
                    log.info("[PortalTransfer] claimed transfer={} player={} target={}",
                            transfer.transferId(), playerId, targetServer);
                });
                return claimed;
            } catch (SQLException failure) {
                throw new IllegalStateException("Failed to claim portal transfer", failure);
            }
        }, writes);
    }

    /** Consumes the arrival payload once; the in-flight record remains until ServerConnectedEvent. */
    public Optional<PortalTransfer> consumeHandoff(UUID playerId, String requestingServer) {
        PortalTransfer available = handoffsAvailable.get(playerId);
        if (available == null || !available.targetServer().equalsIgnoreCase(requestingServer)) {
            return Optional.empty();
        }
        PortalTransfer transfer = handoffsAvailable.remove(playerId);
        if (transfer != null) {
            log.info("[PortalTransfer] consumed handoff transfer={} player={} target={}",
                    transfer.transferId(), playerId, transfer.targetServer());
        }
        return Optional.ofNullable(transfer);
    }

    public CompletableFuture<Void> connectionFailed(UUID playerId, PortalTransfer claimed) {
        if (!handoffsAvailable.remove(playerId, claimed)) {
            // The destination login already consumed the handoff. Never replay it.
            return CompletableFuture.completedFuture(null);
        }
        inFlightTransfers.remove(playerId, claimed);
        pendingTransfers.put(playerId, claimed.withStatus(PortalTransfer.Status.PENDING));
        return runDatabase("restore portal transfer", () -> repository.restorePending(claimed.transferId()));
    }

    public CompletableFuture<Void> completeConnectedTransfer(UUID playerId, String connectedServer) {
        PortalTransfer transfer = inFlightTransfers.get(playerId);
        if (transfer == null || !transfer.targetServer().equalsIgnoreCase(connectedServer)) {
            return CompletableFuture.completedFuture(null);
        }
        inFlightTransfers.remove(playerId, transfer);
        handoffsAvailable.remove(playerId, transfer);
        return runDatabase("complete portal transfer", () -> repository.complete(transfer.transferId()));
    }

    public CompletableFuture<Void> cancelActiveTransfer(UUID playerId) {
        pendingTransfers.remove(playerId);
        handoffsAvailable.remove(playerId);
        inFlightTransfers.remove(playerId);
        return runDatabase("cancel portal transfer", () -> repository.cancelActive(playerId));
    }

    private CompletableFuture<Void> runDatabase(String operation, SqlOperation action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (SQLException failure) {
                throw new IllegalStateException("Failed to " + operation, failure);
            }
        }, writes).whenComplete((ignored, failure) -> {
            if (failure != null) log.error("[PortalTransfer] {} failed", operation, failure);
        });
    }

    @Override
    public void close() {
        writes.shutdown();
        try {
            if (!writes.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[PortalTransfer] Timed out waiting for queued database writes during shutdown");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
