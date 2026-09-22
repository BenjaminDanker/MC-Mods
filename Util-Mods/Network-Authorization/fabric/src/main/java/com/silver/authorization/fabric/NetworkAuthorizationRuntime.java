package com.silver.authorization.fabric;

import com.silver.authorization.Authorization;
import com.silver.authorization.CommandCatalog;
import com.silver.authorization.CommandCatalogCodec;
import com.silver.authorization.AuthorizationProtocolCodec;
import com.silver.authorization.AuthorizationSnapshotStore;
import com.silver.authorization.AuthorizationSubject;
import com.silver.authorization.AuthorizationSyncRequest;
import com.silver.authorization.BackendRequestSigner;
import com.silver.authorization.ServerId;
import com.silver.authorization.SnapshotApplyResult;
import com.silver.authorization.SnapshotSigner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/** Authenticated Fabric receiver and immutable local snapshot cache. */
public final class NetworkAuthorizationRuntime implements Authorization {
    private static final Duration SNAPSHOT_LEASE = Duration.ofMinutes(2);
    // Revisions become effective quickly after an audited Velocity write while snapshots remain
    // player-bound, signed, and local to this backend.
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CATALOG_REFRESH = Duration.ofSeconds(30);
    private static final Duration CATALOG_RETRY = Duration.ofSeconds(15);

    private final FabricAuthorizationConfig config;
    private final Logger log;
    private final Clock clock = Clock.systemUTC();
    private final UUID backendEpoch = UUID.randomUUID();
    private final AuthorizationSnapshotStore snapshots = new AuthorizationSnapshotStore(clock, new SnapshotSigner());
    private final BackendRequestSigner requestSigner = new BackendRequestSigner();
    private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastRequest = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCommandSyncRevision = new ConcurrentHashMap<>();
    private final Set<UUID> initialSnapshotLogged = ConcurrentHashMap.newKeySet();
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong catalogGeneration = new AtomicLong();
    private final ExecutorService catalogSender = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "NetworkAuthorization-CatalogSender");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService catalogRefresh = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "NetworkAuthorization-CatalogRefresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean catalogSendInFlight = new AtomicBoolean();
    private volatile String lastCatalogFingerprint = "";
    private volatile String lastCatalogAttemptedFingerprint = "";
    private volatile Instant lastCatalogAcceptedAt = Instant.EPOCH;
    private volatile Instant lastCatalogAttemptAt = Instant.EPOCH;

    NetworkAuthorizationRuntime(FabricAuthorizationConfig config, Logger log) {
        this.config = config;
        this.log = log;
        config.serverId().ifPresent(server -> snapshots.beginServerSession(server, backendEpoch));
    }

    void register() {
        ServerPlayNetworking.registerGlobalReceiver(AuthorizationPayload.ID, (payload, context) ->
                context.server().execute(() -> applyResponse(context.server(), context.player(), payload.bytes())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            requestSnapshot(handler.player);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Some mod command registrars append nodes after the Commands constructor returns.
            // Index the completed runtime dispatcher before catalog publication or player joins.
            CommandPolicyRuntime.indexDispatcher(server.getCommands().getDispatcher().getRoot());
            maybeSendCommandCatalog();
            // Keep catalogs current even when an empty backend pauses its game tick loop,
            // including after Velocity restarts and loses its in-memory catalog store.
            catalogRefresh.scheduleWithFixedDelay(this::maybeSendCommandCatalog,
                    5, 5, TimeUnit.SECONDS);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ticks.incrementAndGet() % 20 != 0) return;
            Instant now = clock.instant();
            pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            snapshots.purgeExpired();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Instant sentAt = lastRequest.get(player.getUUID());
                if (sentAt == null || !sentAt.plus(REQUEST_INTERVAL).isAfter(now)) requestSnapshot(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID player = handler.player.getUUID();
            pending.entrySet().removeIf(entry -> entry.getValue().player().equals(player));
            lastRequest.remove(player);
            lastCommandSyncRevision.remove(player);
            // A proxy outage also looks like a player disconnect. Retain the accepted decision only
            // until its signed lease expires; a backend restart clears the entire cache by epoch.
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            config.serverId().ifPresent(id -> snapshots.endServerSession(id, backendEpoch));
            pending.clear();
            lastRequest.clear();
            catalogRefresh.shutdownNow();
            catalogSender.shutdownNow();
        });
    }

    public ServerId serverId() {
        return config.serverId().orElseGet(() -> ServerId.of("unconfigured"));
    }

    public UUID backendEpoch() { return backendEpoch; }
    public Optional<AuthorizationSnapshotStore> snapshotStore() { return Optional.of(snapshots); }

    private void requestSnapshot(ServerPlayer player) {
        Optional<ServerId> server = config.serverId();
        Optional<byte[]> key = config.backendKey();
        if (server.isEmpty() || key.isEmpty()) return;
        Instant now = clock.instant();
        UUID nonce = UUID.randomUUID();
        AuthorizationSyncRequest unsigned = AuthorizationSyncRequest.unsigned(
                server.orElseThrow().value(), player.getUUID(), server.orElseThrow(), backendEpoch, nonce, now);
        AuthorizationSyncRequest signed = requestSigner.sign(unsigned, key.orElseThrow());
        pending.put(nonce, new PendingRequest(player.getUUID(), server.orElseThrow(), backendEpoch,
                now.plus(REQUEST_TIMEOUT)));
        lastRequest.put(player.getUUID(), now);
        try {
            ServerPlayNetworking.send(player, new AuthorizationPayload(
                    AuthorizationProtocolCodec.encodeRequest(signed)));
        } catch (RuntimeException failure) {
            pending.remove(nonce);
            log.debug("[NetworkAuthorization] Snapshot request could not be sent: {}", failure.toString());
        }
    }

    private void maybeSendCommandCatalog() {
        Optional<ServerId> server = config.serverId();
        Optional<byte[]> key = config.backendKey();
        if (server.isEmpty() || key.isEmpty() || config.catalogHost().isBlank()) return;
        List<com.silver.authorization.CommandCatalogEntry> entries = CommandPolicyRuntime.catalogEntries();
        String fingerprint = entries.stream().map(entry -> entry.path().canonical() + "\0" + entry.executable()
                + "\0" + entry.source().orElse("") + "\0" + entry.modId().orElse("")).collect(java.util.stream.Collectors.joining("\n"));
        Instant now = clock.instant();
        boolean changed = !fingerprint.equals(lastCatalogFingerprint);
        boolean refreshDue = !lastCatalogAcceptedAt.plus(CATALOG_REFRESH).isAfter(now);
        boolean retryWindowOpen = !lastCatalogAttemptAt.plus(CATALOG_RETRY).isAfter(now);
        boolean sameRecentAttempt = fingerprint.equals(lastCatalogAttemptedFingerprint) && !retryWindowOpen;
        if ((!changed && !refreshDue) || sameRecentAttempt
                || !catalogSendInFlight.compareAndSet(false, true)) return;
        lastCatalogAttemptAt = now;
        lastCatalogAttemptedFingerprint = fingerprint;
        long generation = catalogGeneration.incrementAndGet();
        try {
            catalogSender.execute(() -> {
                try {
                    CommandCatalog catalog = new CommandCatalog(CommandCatalog.CURRENT_PROTOCOL_VERSION,
                            server.orElseThrow(), backendEpoch, generation, UUID.randomUUID(),
                            clock.instant(), entries);
                    byte[] signed = CommandCatalogCodec.encodeSigned(catalog, key.orElseThrow());
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(config.catalogHost(), config.catalogPort()), 3_000);
                        socket.setSoTimeout(5_000);
                        try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                             DataInputStream input = new DataInputStream(socket.getInputStream())) {
                            output.writeUTF(server.orElseThrow().value());
                            output.writeInt(signed.length);
                            output.write(signed);
                            output.flush();
                            if (!input.readBoolean()) throw new IllegalStateException("Velocity rejected runtime catalog");
                        }
                    }
                    lastCatalogFingerprint = fingerprint;
                    lastCatalogAcceptedAt = clock.instant();
                    log.info("[CommandPolicy] Runtime command catalog synchronized server={} paths={} generation={} epoch={}",
                            server.orElseThrow(), entries.size(), generation, backendEpoch);
                } catch (Exception failure) {
                    log.warn("[CommandPolicy] Runtime command catalog sync failed for {}: {}",
                            server.orElseThrow(), failure.toString());
                } finally {
                    catalogSendInFlight.set(false);
                }
            });
        } catch (RuntimeException failure) {
            catalogSendInFlight.set(false);
            log.warn("[CommandPolicy] Could not schedule authenticated runtime command catalog: {}", failure.toString());
        }
    }

    private void applyResponse(MinecraftServer server, ServerPlayer player, byte[] frame) {
        if (player == null) return;
        try {
            var signed = AuthorizationProtocolCodec.decodeSnapshot(frame);
            var snapshot = signed.snapshot();
            PendingRequest request = pending.get(snapshot.nonce());
            if (request == null || !request.expiresAt().isAfter(clock.instant())
                    || !request.player().equals(player.getUUID())
                    || !request.server().equals(config.serverId().orElse(null))
                    || !request.epoch().equals(backendEpoch)
                    || !snapshot.subject().equals(AuthorizationSubject.player(player.getUUID()))
                    || !snapshot.serverId().equals(request.server())
                    || !snapshot.backendEpoch().equals(request.epoch())) {
                return;
            }
            byte[] key = config.backendKey().orElse(null);
            if (key == null || Duration.between(snapshot.issuedAt(), snapshot.expiresAt()).compareTo(SNAPSHOT_LEASE) > 0) {
                return;
            }
            SnapshotApplyResult result = snapshots.apply(signed, AuthorizationSubject.player(player.getUUID()),
                    request.server(), key);
            if (result == SnapshotApplyResult.APPLIED) {
                pending.remove(snapshot.nonce(), request);
                Long previousCommandRevision = lastCommandSyncRevision.put(player.getUUID(), snapshot.revision());
                if (previousCommandRevision == null || previousCommandRevision.longValue() != snapshot.revision()) {
                    server.getCommands().sendCommands(player);
                }
                if (initialSnapshotLogged.add(player.getUUID())) {
                    log.info("[NetworkAuthorization] Accepted initial snapshot revision={} subject={} server={} backendEpoch={} expiresAt={}",
                            snapshot.revision(), player.getUUID(), request.server(), snapshot.backendEpoch(), snapshot.expiresAt());
                } else {
                    log.debug("[NetworkAuthorization] Applied revision={} for player={} server={}",
                            snapshot.revision(), player.getUUID(), request.server());
                }
            } else {
                log.debug("[NetworkAuthorization] Rejected snapshot result={} server={}", result, request.server());
            }
        } catch (RuntimeException malformed) {
            log.debug("[NetworkAuthorization] Rejected malformed snapshot frame: {}", malformed.toString());
        }
    }

    @Override
    public com.silver.authorization.AuthorizationDecision decide(
            AuthorizationSubject subject,
            com.silver.authorization.PermissionNode permission,
            ServerId server) {
        return snapshots.decide(subject, permission, server);
    }

    @Override
    public boolean has(AuthorizationSubject subject,
                       com.silver.authorization.PermissionNode permission,
                       ServerId server) {
        return snapshots.has(subject, permission, server);
    }

    private record PendingRequest(UUID player, ServerId server, UUID epoch, Instant expiresAt) {}
}
