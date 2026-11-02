package com.silver.routing.state;

import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MariaDB-backed routing authority with local read-through state for Velocity's synchronous events.
 * All mutations are serialized on a dedicated virtual-thread executor.
 */
public final class RoutingStateService implements AutoCloseable {
    private final MariaDbRoutingRepository repository;
    private final Logger log;
    private final Clock clock;
    private final ExecutorService writes = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("wakeup-routing-db-", 0).factory());

    private final ConcurrentHashMap<UUID, String> preferredServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastListedServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> visitedServers = new ConcurrentHashMap<>();

    public RoutingStateService(MariaDbRoutingRepository repository, Logger log, Clock clock) {
        this.repository = repository;
        this.log = log;
        this.clock = clock;
    }

    public static RoutingStateService initialize(Logger log) throws IOException, SQLException {
        MariaDbRoutingRepository repository = new MariaDbRoutingRepository(DatabaseSettings.loadDefault());
        boolean success = false;
        try {
            repository.migrate();
            RoutingStateService service = new RoutingStateService(
                    repository, log, Clock.systemUTC());
            service.installSnapshot(repository.loadSnapshot(Instant.now()));
            success = true;
            return service;
        } finally {
            if (!success) repository.close();
        }
    }

    private void installSnapshot(RoutingSnapshot snapshot) {
        preferredServers.clear();
        preferredServers.putAll(snapshot.preferredServers());
        lastListedServers.clear();
        lastListedServers.putAll(snapshot.lastListedServers());
        visitedServers.clear();
        snapshot.visitedServers().forEach((player, servers) ->
                visitedServers.put(player, ConcurrentHashMap.newKeySet(servers.size())));
        snapshot.visitedServers().forEach((player, servers) -> visitedServers.get(player).addAll(servers));
        log.info("[RoutingState] Loaded players={}, visitOwners={}",
                preferredServers.size(), visitedServers.size());
    }

    public Optional<String> preferredServer(UUID playerId) {
        return Optional.ofNullable(preferredServers.get(playerId));
    }

    public DataSource dataSource() {
        return repository.dataSource();
    }

    public Optional<String> lastListedServer(UUID playerId) {
        return Optional.ofNullable(lastListedServers.get(playerId));
    }

    public Set<String> visitedServers(UUID playerId) {
        return Set.copyOf(visitedServers.getOrDefault(playerId, Set.of()));
    }

    /**
     * Visited servers re-read from the database and kept in memory. Another process (a Babylon branch switch)
     * can change a player's visits, so the exclusive-branch check must not trust the copy loaded at startup.
     */
    public Set<String> refreshVisitedServers(UUID playerId) {
        try {
            Set<String> fresh = repository.loadVisits(playerId);
            Set<String> held = ConcurrentHashMap.newKeySet(fresh.size());
            held.addAll(fresh);
            visitedServers.put(playerId, held);
            return Set.copyOf(fresh);
        } catch (SQLException failure) {
            log.warn("[RoutingState] Could not refresh visits for {}; using the in-memory copy", playerId, failure);
            return visitedServers(playerId);
        }
    }

    /** Finished story branches, read fresh. Fails closed: if the database cannot answer, nothing counts as finished. */
    public Set<String> completedBranches(UUID playerId) {
        try {
            return repository.loadCompletedBranches(playerId);
        } catch (SQLException failure) {
            log.warn("[RoutingState] Could not read story completion for {}; treating as none", playerId, failure);
            return Set.of();
        }
    }

    public CompletableFuture<Void> recordSuccessfulConnection(UUID playerId, String server,
                                                               boolean isListedServer) {
        preferredServers.put(playerId, server);
        visitedServers.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(server);
        String listed = null;
        if (isListedServer) {
            lastListedServers.put(playerId, server);
            listed = server;
        }
        String finalListed = listed;
        return runDatabase("record successful connection", () ->
                repository.recordSuccessfulConnection(playerId, server, finalListed));
    }

    public CompletableFuture<Void> setPreferredServer(UUID playerId, String server) {
        preferredServers.put(playerId, server);
        return runDatabase("set preferred server", () -> repository.setPreferredServer(playerId, server));
    }

    private CompletableFuture<Void> runDatabase(String operation, SqlOperation action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (SQLException failure) {
                throw new IllegalStateException("Failed to " + operation, failure);
            }
        }, writes).whenComplete((ignored, failure) -> {
            if (failure != null) log.error("[RoutingState] {} failed", operation, failure);
        });
    }

    @Override
    public void close() {
        writes.shutdown();
        try {
            if (!writes.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[RoutingState] Timed out waiting for queued database writes during shutdown");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        repository.close();
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
