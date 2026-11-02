package com.silver.authorization.velocity;

import com.google.inject.Inject;
import com.silver.authorization.AuthorizationProtocolCodec;
import com.silver.authorization.AuthorizationSyncRequest;
import com.silver.authorization.ServerId;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Central permission authority for the proxy: DB-backed state, backend snapshot sync, command catalogs and /auth. */
@Plugin(id = "network-authorization", name = "Network Authorization", version = "0.1.0")
public final class NetworkAuthorizationVelocity {
    private static final MinecraftChannelIdentifier AUTHORIZATION_SYNC_CHANNEL =
            MinecraftChannelIdentifier.from("silverauth:sync_v1");
    private static final MinecraftChannelIdentifier COMMAND_CATALOG_CHANNEL =
            MinecraftChannelIdentifier.from("silverauth:command_catalog_v1");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private final CommandCatalogStore commandCatalogStore;
    private HikariDataSource dataSource;
    private AuthorizationService authorizationService;
    private AuthorizationBackendKeys authorizationBackendKeys;
    private CommandCatalogReceiver commandCatalogReceiver;

    @Inject
    public NetworkAuthorizationVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
        this.commandCatalogStore = new CommandCatalogStore(logger);
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            proxy.getChannelRegistrar().register(AUTHORIZATION_SYNC_CHANNEL);
            proxy.getChannelRegistrar().register(COMMAND_CATALOG_CHANNEL);

            dataSource = openPool(DatabaseSettings.loadDefault());
            Set<ServerId> configuredAuthorizationServers = configuredAuthorizationServers();
            authorizationService = new AuthorizationService(
                    new MariaDbAuthorizationRepository(dataSource), logger, java.time.Clock.systemUTC());
            try {
                authorizationService.initialize(configuredAuthorizationServers);
            } catch (Exception authorizationFailure) {
                logger.error("[Authorization] Cold start has no usable central state; centralized decisions fail closed",
                        authorizationFailure);
            }
            try {
                authorizationBackendKeys = AuthorizationBackendKeys.load(dataDir,
                        configuredAuthorizationServers, logger);
            } catch (IOException keyConfigFailure) {
                logger.error("[Authorization] Could not load backend keys; authenticated snapshot sync is disabled",
                        keyConfigFailure);
            }
            if (authorizationBackendKeys != null) {
                try {
                    commandCatalogReceiver = CommandCatalogReceiver.start(dataDir, authorizationBackendKeys,
                            authorizationService, commandCatalogStore, logger);
                } catch (IOException catalogListenerFailure) {
                    logger.error("[CommandPolicy] Could not start authenticated startup catalog listener; "
                            + "backend command inventories will remain unavailable", catalogListenerFailure);
                }
            }
            proxy.getScheduler().buildTask(this, () -> {
                if (authorizationService == null) return;
                if (authorizationService.hasUsableState()) {
                    authorizationService.refreshDatabaseHealth();
                } else {
                    authorizationService.refreshDatabaseHealth();
                    if (authorizationService.databaseAvailable()) {
                        try { authorizationService.initialize(configuredAuthorizationServers); }
                        catch (Exception retryFailure) {
                            logger.warn("[Authorization] Central authority remains unavailable on cold-start retry: {}",
                                    retryFailure.toString());
                        }
                    }
                }
            }).repeat(Duration.ofSeconds(10)).schedule();

            proxy.getCommandManager().register("auth", new AuthorizationCommand(
                    proxy, commandCatalogStore, authorizationService, new UUIDResolver(proxy, logger), logger));
            ProxyAuthorization.install(authorizationService);
            logger.info("[Authorization] Network Authorization ready");
        } catch (Exception ex) {
            logger.error("Failed to initialize Network Authorization; centralized decisions fail closed", ex);
        }
    }

    private static HikariDataSource openPool(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        // Shaded Velocity plugins do not reliably preserve JDBC's service-loader
        // metadata, so identify the bundled driver explicitly.
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setPoolName("NetworkAuthorization");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getIdentifier().equals(AUTHORIZATION_SYNC_CHANNEL)) {
            handleAuthorizationSyncMessage(event);
        } else if (event.getIdentifier().equals(COMMAND_CATALOG_CHANNEL)) {
            handleCommandCatalogMessage(event);
        }
    }

    @Subscribe
    public void onConnected(ServerConnectedEvent e) {
        AuthorizationService service = authorizationService;
        if (service == null) return;
        UUID knownUuid = e.getPlayer().getUniqueId();
        String knownUsername = e.getPlayer().getUsername();
        proxy.getScheduler().buildTask(this, () -> service.rememberUsername(knownUuid, knownUsername)).schedule();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        ProxyAuthorization.uninstall();
        if (commandCatalogReceiver != null) commandCatalogReceiver.close();
        if (authorizationService != null) authorizationService.close();
        if (dataSource != null) dataSource.close();
    }

    private Set<ServerId> configuredAuthorizationServers() {
        Set<ServerId> configured = new HashSet<>();
        proxy.getAllServers().forEach(server -> configured.add(ServerId.of(server.getServerInfo().getName())));
        Set<ServerId> expected = Set.of("waiting_lobby", "vanilla", "sky-island", "ocean", "desert", "cave", "magic")
                .stream().map(ServerId::of).collect(Collectors.toUnmodifiableSet());
        Set<ServerId> missing = new HashSet<>(expected);
        missing.removeAll(configured);
        if (!missing.isEmpty()) logger.warn("[Authorization] Expected canonical server IDs not present in Velocity config: {}", missing);
        return Set.copyOf(configured);
    }

    private void handleAuthorizationSyncMessage(PluginMessageEvent event) {
        try {
            if (!(event.getSource() instanceof ServerConnection backend)
                    || !(event.getTarget() instanceof Player player)
                    || authorizationService == null || authorizationBackendKeys == null) {
                return;
            }
            ServerId trustedServer = ServerId.of(backend.getServerInfo().getName());
            if (!player.getCurrentServer().map(connection ->
                    connection.getServerInfo().getName().equalsIgnoreCase(trustedServer.value())).orElse(false)) {
                return;
            }
            byte[] frame = event.getData();
            AuthorizationSyncRequest request = AuthorizationProtocolCodec.decodeRequest(frame);
            if (!request.backendId().equals(trustedServer.value())
                    || !request.serverId().equals(trustedServer)
                    || !request.subject().playerUuid().equals(player.getUniqueId())
                    || !authorizationService.currentState().enabledServers().contains(trustedServer)) {
                return;
            }
            byte[] key = authorizationBackendKeys.key(trustedServer).orElse(null);
            if (key == null || !authorizationService.verifySyncRequest(request, key)) {
                logger.debug("[Authorization] Rejected unauthenticated/replayed sync request from {}", trustedServer);
                return;
            }
            var signedSnapshot = authorizationService.signedSnapshot(player.getUniqueId(), trustedServer,
                    request.backendEpoch(), request.nonce(), Instant.now(), Duration.ofMinutes(2), key);
            if (signedSnapshot.isEmpty()) return;
            boolean sent = backend.sendPluginMessage(AUTHORIZATION_SYNC_CHANNEL,
                    AuthorizationProtocolCodec.encodeSnapshot(signedSnapshot.orElseThrow()));
            if (!sent) logger.debug("[Authorization] Snapshot send failed for {} on {}", player.getUniqueId(), trustedServer);
        } catch (RuntimeException failure) {
            logger.warn("[Authorization] Rejected malformed sync message: {}", failure.toString());
        } finally {
            try { event.setResult(PluginMessageEvent.ForwardResult.handled()); }
            catch (Throwable ignored) { }
        }
    }

    private void handleCommandCatalogMessage(PluginMessageEvent event) {
        try {
            if (!(event.getSource() instanceof ServerConnection backend)
                    || !(event.getTarget() instanceof Player player)
                    || authorizationService == null || authorizationBackendKeys == null) return;
            ServerId trustedServer = ServerId.of(backend.getServerInfo().getName());
            if (!player.getCurrentServer().map(connection ->
                    connection.getServerInfo().getName().equalsIgnoreCase(trustedServer.value())).orElse(false)
                    || !authorizationService.currentState().enabledServers().contains(trustedServer)) return;
            byte[] key = authorizationBackendKeys.key(trustedServer).orElse(null);
            if (key == null) return;
            commandCatalogStore.accept(event.getData(), trustedServer, key, Instant.now());
        } catch (RuntimeException failure) {
            logger.warn("[CommandPolicy] Could not process backend command catalog: {}", failure.toString());
        } finally {
            try { event.setResult(PluginMessageEvent.ForwardResult.handled()); }
            catch (Throwable ignored) { }
        }
    }
}
