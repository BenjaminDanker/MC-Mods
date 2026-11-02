package com.silver.portals.velocity;

import com.google.inject.Inject;
import com.silver.authorization.PortalRequestCodec;
import com.silver.authorization.velocity.DatabaseSettings;
import com.silver.wakeup.api.Routing;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerLoginPluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Portal hand-off between backends: the /wl portal command, the signed portals:transfer requests from the Portals mod,
 * the portals:arrival login reply, durable transfers, and the item return used by /return. Player movement itself
 * (waiting for a target to start, holding lobby) belongs to the routing plugin, reached through {@link Routing}.
 */
@Plugin(id = "portals", name = "Portals", version = "1.0.0",
        dependencies = {@Dependency(id = "wakeuplobby"), @Dependency(id = "network-authorization")})
public final class PortalsVelocity {
    private static final MinecraftChannelIdentifier PORTAL_HANDOFF_CHANNEL = MinecraftChannelIdentifier.from("portals:arrival");
    private static final MinecraftChannelIdentifier PORTAL_REQUEST_CHANNEL = MinecraftChannelIdentifier.from("portals:transfer");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private HikariDataSource dataSource;
    private PortalTransferStore store;
    private volatile PortalHandoffService handoffService;
    private volatile PortalCommandHandler commandHandler;
    private final PortalTokenVerifier tokenVerifier;
    private final PortalRequestVerifier requestVerifier;
    private final ItemReturnService itemReturn;

    @Inject
    public PortalsVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
        this.tokenVerifier = new PortalTokenVerifier(logger);
        this.requestVerifier = new PortalRequestVerifier(logger);
        this.itemReturn = new ItemReturnService(logger);
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            proxy.getChannelRegistrar().register(PORTAL_HANDOFF_CHANNEL);
            proxy.getChannelRegistrar().register(PORTAL_REQUEST_CHANNEL);
            proxy.getChannelRegistrar().register(ItemReturnService.CHANNEL);

            PortalsConfig.ensureDefault(dataDir, logger);
            applyConfig();

            dataSource = openPool(DatabaseSettings.loadDefault());
            store = PortalTransferStore.initialize(new PortalTransferRepository(dataSource), logger);
            handoffService = new PortalHandoffService(store, logger);
            commandHandler = new PortalCommandHandler(logger, new VelocityDependencies());

            // The routing plugin tells us when an admin or /return cancels a wait, and asks us to claim a pending
            // transfer right before it connects the player to the target; /return asks us to remove special items.
            Routing.onStickyCancelled(playerId -> handoffService.cancelActiveTransfer(playerId));
            Routing.setStickyConnectHook(this::claimBeforeConnect);
            Routing.setItemReturn(itemReturn);
            Routing.onReload(() -> {
                try {
                    applyConfig();
                } catch (Exception failure) {
                    logger.warn("[Portals] Could not reload the portal secrets: {}", failure.toString());
                }
            });

            registerWlCommand();
            logger.info("[Portals] Portals ready");
        } catch (Exception ex) {
            logger.error("Failed to initialize Portals", ex);
        }
    }

    private void applyConfig() throws java.io.IOException {
        PortalsConfig config = PortalsConfig.load(dataDir);
        tokenVerifier.updateSecrets(config.globalSecret(), config.perPortalSecrets());
        requestVerifier.updateSecrets(config.backendRequestSecrets());
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (store != null) store.close();
        if (dataSource != null) dataSource.close();
    }

    /**
     * Claims the pending transfer for {@code target} (if any) so the arrival payload is ready when the backend asks for
     * it; the returned runnable puts the transfer back when the connection fails.
     */
    private CompletableFuture<Runnable> claimBeforeConnect(UUID playerId, String target) {
        PortalHandoffService service = handoffService;
        if (service == null) {
            return CompletableFuture.completedFuture(() -> { });
        }
        return service.claimForTarget(playerId, target).thenApply(claimed ->
                () -> claimed.ifPresent(transfer -> service.connectionFailed(playerId, transfer)));
    }

    /**
     * Event order. The routing plugin's own ServerConnectedEvent handler is a plain @Subscribe, i.e. PostOrder.NORMAL.
     * Velocity runs the handlers of one event by PostOrder (FIRST, EARLY, NORMAL, LATE, LAST) across all plugins, so
     * this FIRST handler always runs before it, whatever the plugin load order. The handlers are synchronous (void), so
     * this one has finished when the routing plugin's runs: a resumed transfer already has sticky state when the
     * routing plugin decides about fresh-player admission, and admission is bypassed for players with sticky state
     * exactly as before the split. For gameplay servers the order does not matter (completing a transfer and recording
     * a visit are independent), FIRST just keeps both cases in one handler.
     */
    @Subscribe(order = PostOrder.FIRST)
    public void onConnected(ServerConnectedEvent e) {
        PortalHandoffService service = handoffService;
        String holding = Routing.holdingServer();
        if (service == null || holding == null) {
            return;
        }
        UUID playerId = e.getPlayer().getUniqueId();
        String server = e.getServer().getServerInfo().getName();

        if (!server.equalsIgnoreCase(holding)) {
            service.completeConnectedTransfer(playerId, server);
            return;
        }

        Optional<PortalTransfer> pending = service.pendingTransfer(playerId);
        if (pending.isPresent() && !Routing.hasStickyState(playerId)) {
            PortalTransfer transfer = pending.orElseThrow();
            logger.info("[PortalTransfer] Resuming transfer={} for {} toward {}",
                    transfer.transferId(), e.getPlayer().getUsername(), transfer.targetServer());
            Routing.resumeStickyWait(playerId, transfer.targetServer(), transfer.sourceServer());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getIdentifier().equals(PORTAL_REQUEST_CHANNEL)) {
            handlePortalRequestMessage(event);
        } else if (event.getIdentifier().equals(ItemReturnService.CHANNEL)) {
            itemReturn.onPluginMessage(event);
        }
    }

    @Subscribe
    public void onLoginPluginMessage(ServerLoginPluginMessageEvent event) {
        if (!event.getIdentifier().equals(PORTAL_HANDOFF_CHANNEL)) {
            return;
        }

        var player = event.getConnection().getPlayer();
        if (player == null) {
            logger.warn("[Portals] Login plugin message received without player context");
            event.setResult(ServerLoginPluginMessageEvent.ResponseResult.unknown());
            return;
        }

        UUID playerId = player.getUniqueId();
        String requestingServer = event.getConnection().getServerInfo().getName();
        byte[] payload = handoffService.consumeResponsePayload(playerId, requestingServer);
        event.setResult(ServerLoginPluginMessageEvent.ResponseResult.reply(payload));
    }

    private void handlePortalRequestMessage(PluginMessageEvent event) {
        try {
            if (!(event.getSource() instanceof ServerConnection backendConn)) {
                logger.warn("[Portals] Ignoring portal_request from non-backend source: {}", event.getSource());
                return;
            }

            String backendName = backendConn.getServerInfo().getName();
            var requestOpt = PortalRequestCodec.decode(event.getData());
            if (requestOpt.isEmpty()) {
                logger.warn("[Portals] Invalid portal_request payload from backend {}", backendName);
                return;
            }

            var request = requestOpt.get();
            if (commandHandler == null) {
                logger.warn("[Portals] Portal request received before initialization completed (backend={})", backendName);
                return;
            }

            if (!requestVerifier.verify(backendName, request)) {
                return;
            }

            var playerOpt = proxy.getPlayer(request.playerId());
            if (playerOpt.isEmpty()) {
                logger.warn("[Portals] Portal request for offline player {} (backend={})", request.playerId(), backendName);
                return;
            }
            Player player = playerOpt.get();

            String target = request.targetServer();
            var targetReg = proxy.getServer(target);
            if (targetReg.isEmpty()) {
                logger.warn("[Portals] Portal request rejected: unknown target server '{}' (backend={})", target, backendName);
                return;
            }

            String arrivalPortal = request.arrivalPortal();
            boolean handled = commandHandler.handleAuthorized(
                    player,
                    target,
                    Optional.ofNullable(arrivalPortal).filter(s -> !s.isBlank()));
            if (!handled) {
                logger.warn("[Portals] Portal request was verified but handoff setup failed for player {} target {}",
                        player.getUsername(), target);
            }
        } catch (Exception ex) {
            logger.warn("[Portals] Failed handling portal_request: {}", ex.toString());
        } finally {
            // proxy-internal; do not forward to client
            try {
                event.setResult(PluginMessageEvent.ForwardResult.handled());
            } catch (Throwable ignored) {
            }
        }
    }

    private void registerWlCommand() {
        CommandMeta meta = proxy.getCommandManager()
                .metaBuilder("wl")
                .plugin(this)
                .hint(LiteralArgumentBuilder.<CommandSource>literal("wl")
                        .requires(source -> false)
                        .build())
                .build();

        proxy.getCommandManager().register(meta, new SimpleCommand() {
            @Override
            public void execute(Invocation inv) {
                if (!(inv.source() instanceof Player player)) {
                    inv.source().sendMessage(Component.text("Players only."));
                    return;
                }

                String[] args = inv.arguments();
                if (args.length < 3 || !args[0].equalsIgnoreCase("portal")) {
                    return;
                }

                String target = args[1];
                String token = args[2];
                String arrivalPortal = args.length > 3 ? args[3] : null;

                logger.info("[Portals] /wl portal: player={} target={} arrivalPortal={}",
                        player.getUsername(), target, arrivalPortal);

                if (commandHandler == null) {
                    logger.warn("[Portals] /wl portal invoked before initialization completed");
                    player.sendMessage(Component.text("§cPortal system not ready yet. Try again shortly."));
                    return;
                }

                commandHandler.handle(player, target, token, Optional.ofNullable(arrivalPortal));
            }
        });
    }

    private static HikariDataSource openPool(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        // Shaded Velocity plugins do not reliably preserve JDBC's service-loader metadata; name the bundled driver.
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setPoolName("Portals");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private final class VelocityDependencies implements PortalCommandHandler.Dependencies {
        @Override
        public boolean verifyPortalToken(String target, String token) {
            return tokenVerifier.verify(target, token);
        }

        @Override
        public CompletionStage<?> createTransfer(UUID playerId, String sourceServer, String targetServer, String arrivalPortal) {
            return handoffService.createTransfer(playerId, sourceServer, targetServer, arrivalPortal);
        }

        @Override
        public Optional<String> currentServerName(Player player) {
            return player.getCurrentServer().map(cs -> cs.getServerInfo().getName());
        }

        @Override
        public Optional<PortalCommandHandler.HoldingConnection> resolveHoldingServer(
                Player player, String target, Optional<String> originServer) {
            return proxy.getServer(Routing.holdingServer())
                    .map(reg -> () -> player.createConnectionRequest(reg).fireAndForget());
        }

        @Override
        public void beginStickyWait(UUID playerId, String target, Optional<String> originServer) {
            Routing.beginStickyWait(playerId, target, originServer.orElse(null));
        }

        @Override
        public void markInternalOnce(UUID playerId, String target) {
            Routing.markInternalOnce(playerId, target);
        }

        @Override
        public void notifyInvalidToken(Player player) {
            player.sendMessage(Component.text("§cInvalid portal token."));
        }

        @Override
        public void notifyTransferFailure(Player player) {
            player.sendMessage(Component.text("§cCould not create the server transfer. Please try again."));
        }

        @Override
        public String holdingServerName() {
            return Routing.holdingServer();
        }

        @Override
        public CompletionStage<Boolean> tryDirectConnect(Player player, String target) {
            Optional<RegisteredServer> registered = proxy.getServer(target);
            if (registered.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            UUID playerId = player.getUniqueId();
            CompletableFuture<Boolean> moved = new CompletableFuture<>();
            registered.get().ping().orTimeout(1, TimeUnit.SECONDS).whenComplete((pong, pingFailure) -> {
                if (pingFailure != null) {
                    moved.complete(false); // not up (or slow): the lobby wakes it and waits
                    return;
                }
                handoffService.claimForTarget(playerId, target).whenComplete((claimed, claimFailure) -> {
                    if (claimFailure != null) {
                        moved.complete(false);
                        return;
                    }
                    Routing.markInternalOnce(playerId, registered.get().getServerInfo().getName());
                    player.createConnectionRequest(registered.get()).connect().whenComplete((result, connectFailure) -> {
                        if (connectFailure == null && result != null && result.isSuccessful()) {
                            moved.complete(true);
                            return;
                        }
                        // Put the transfer back so the lobby route can claim it again.
                        claimed.map(transfer -> handoffService.connectionFailed(playerId, transfer))
                                .orElse(CompletableFuture.completedFuture(null))
                                .whenComplete((ignored, alsoIgnored) -> moved.complete(false));
                    });
                });
            });
            return moved;
        }
    }
}
