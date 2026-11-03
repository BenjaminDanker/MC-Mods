package com.silver.routing.plugin;

import com.google.inject.Inject;
import com.silver.routing.session.StickyRouter;
import com.silver.authorization.velocity.ProxyAuthorization;
import com.silver.authorization.PermissionNode;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.ServerId;
import com.silver.ondemand.OnDemandServers;
import com.silver.routing.api.Routing;
import com.silver.routing.config.LobbyConfig;
import com.silver.routing.config.LobbyConfigLoader;
import com.silver.routing.state.RoutingStateService;
import com.silver.routing.config.ReturnSpecial;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerLoginPluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main Velocity proxy plugin for lobby management and player state tracking.
 * 
 * <p>Provides sophisticated player routing through a configurable lobby server,
 * maintains player session state across server transitions, and integrates with
 * the Portals mod for secure inter-server handoffs.
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Managing player lobby transitions and grace periods</li>
 *   <li>Tracking player state and visit history</li>
 *   <li>Routing players between configured server groups</li>
 *   <li>Handling portal authentication and secure handoffs</li>
 *   <li>Keeping servers alive via configurable ping intervals</li>
 * </ul>
 */
@Plugin(id = "player-routing", name = "Player Routing", version = "1.0.0",
        dependencies = {@Dependency(id = "network-authorization"), @Dependency(id = "on-demand-servers")})
public class PlayerRoutingPlugin {
    private static final PermissionNode SERVER_PERMISSION = PermissionNodes.ROUTING_SERVER;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    // config/runtime
    private LobbyConfigLoader configLoader;
    private RuntimeState runtime;
    private RoutingStateService routingState;
    private CommandRegistrar commandRegistrar;

    private static final MinecraftChannelIdentifier RETURN_OVERWORLD_CHANNEL =
        MinecraftChannelIdentifier.from("routing:return_overworld");

    public String computeReturnDestination(UUID playerId) {
        LobbyConfig cfg = runtime == null ? null : runtime.currentConfig();
        List<String> order = cfg == null ? List.of() : cfg.returnServerOrder();
        if (order.isEmpty()) {
            return firstDefault();
        }

        String lastListed = routingState.lastListedServer(playerId).orElse(null);
        String candidate = firstMatchingIgnoreCase(order, lastListed).orElse(order.get(0));

        String last = routingState.preferredServer(playerId).orElse(null);
        if (last != null && candidate.equalsIgnoreCase(last)) {
            int idx = indexOfIgnoreCase(order, candidate);
            if (idx > 0) {
                return order.get(idx - 1);
            }
            return order.get(0);
        }

        return candidate;
    }

    public List<String> computeReturnLossDisplayNames(UUID playerId) {
        if (runtime == null) {
            return List.of();
        }

        // Loss list should be based on the server we're giving up on (/return away from),
        // which is the sticky-wait target that grace expired on.
        String serverKey = runtime.stickyRouter().returnTargetServer(playerId);
        if (serverKey == null || serverKey.isBlank()) {
            serverKey = runtime.stickyRouter().returnOriginServer(playerId);
        }
        if (serverKey == null || serverKey.isBlank()) {
            return List.of();
        }

        LobbyConfig cfg = runtime.currentConfig();
        if (cfg == null) {
            return List.of();
        }

        for (var entry : cfg.returnSpecials().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(serverKey)) {
                return entry.getValue().stream().map(s -> s.displayName()).toList();
            }
        }
        return List.of();
    }

    List<ReturnSpecial> computeReturnSpecials(UUID playerId) {
        if (runtime == null) {
            return List.of();
        }

        String serverKey = runtime.stickyRouter().returnTargetServer(playerId);
        if (serverKey == null || serverKey.isBlank()) {
            serverKey = runtime.stickyRouter().returnOriginServer(playerId);
        }
        if (serverKey == null || serverKey.isBlank()) {
            return List.of();
        }

        LobbyConfig cfg = runtime.currentConfig();
        if (cfg == null) {
            return List.of();
        }

        for (var entry : cfg.returnSpecials().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(serverKey)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    public String formatLossList(List<String> displayNames) {
        if (displayNames == null || displayNames.isEmpty()) {
            return "nothing";
        }
        return displayNames.stream().collect(Collectors.joining(", "));
    }

    private static Optional<String> firstMatchingIgnoreCase(List<String> candidates, String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (String c : candidates) {
            if (c != null && c.equalsIgnoreCase(value)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private static int indexOfIgnoreCase(List<String> list, String value) {
        if (value == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s != null && s.equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    @Inject
    public PlayerRoutingPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            proxy.getChannelRegistrar().register(RETURN_OVERWORLD_CHANNEL);

            configLoader = new LobbyConfigLoader(logger, dataDir);
            configLoader.ensureDefaultConfig();
            routingState = RoutingStateService.initialize(logger);
            runtime = new RuntimeState(proxy, this, logger);
            LobbyConfig config = loadConfig();

            commandRegistrar = new CommandRegistrar(proxy, runtime, this, logger);
            Routing.install(new RoutingBackend());
            // A server's resource pack would otherwise stay on the player's game after they move to a server without it.
            proxy.getEventManager().register(this, new ServerPackCleanup(logger));

            // On-demand start/stop, health and capacity answers live in the on-demand-servers plugin; feed it what only this plugin knows.
            OnDemandServers.setWakeHost(runtime::wakeHost);
            OnDemandServers.setHoldingServer(runtime::holdingServer);
            // Prime both caches immediately; the plugin keeps them fresh. (The waiting-lobby plugin adds the waiting servers.)
            OnDemandServers.prime();

            logger.info("[PlayerRouting] Loaded. Holding={}, grace={}s, interval={}s",
                    runtime.holdingServer(),
                    config.graceSec(),
                    config.pingEverySec());
        } catch (Exception ex) {
            logger.error("Failed to initialize Player Routing", ex);
        }
    }

    void requestReturnOverworldIfNeeded(Player player, String destServer, String originServer) {
        if (player == null || destServer == null || destServer.isBlank() || runtime == null) {
            return;
        }

        if (originServer == null || !originServer.equalsIgnoreCase(destServer)) {
            return;
        }

        var current = player.getCurrentServer();
        if (current.isEmpty() || current.get().getServerInfo() == null) {
            return;
        }

        String currentName = current.get().getServerInfo().getName();
        if (!currentName.equalsIgnoreCase(destServer)) {
            return;
        }

        current.get().sendPluginMessage(RETURN_OVERWORLD_CHANNEL, new byte[0]);
        logger.info("[PlayerRouting] Requested overworld return for {} on server {} (origin={})",
            player.getUsername(),
            destServer,
            originServer);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (commandRegistrar == null) {
            logger.error("[PlayerRouting] Command registrar not initialised; skipping command registration");
            return;
        }
        commandRegistrar.register();
    }

    /**
     * Sticky behavior + policy gates:
     * - Internal plugin-initiated moves are always allowed.
     * - Non-admins cannot use /server at all (manual server switches are denied).
     * - Even admins must be allowed; they bypass tier restrictions.
     * - Downward-only tier rule for normal fallback/manual attempts.
     */
    @Subscribe
    public void onPreConnect(ServerPreConnectEvent e) {
        var player = e.getPlayer();
        var destOpt = e.getResult().getServer();
        
        logger.info("[PlayerRouting] onPreConnect: player={} destServer={}", 
                   player.getUsername(), 
                   destOpt.map(s -> s.getServerInfo().getName()).orElse("none"));
        
        if (destOpt.isEmpty()) {
            logger.warn("[PlayerRouting] onPreConnect: no destination server for {}", player.getUsername());
            return;
        }

        String requested = destOpt.get().getServerInfo().getName();

        // Exclusive branches: once a player has entered one server of the group they can never
        // enter another, however they ask (portal, /server, or login routing). Staff bypass.
        if (!hasBypass(player) && lockedOutOfBranch(player.getUniqueId(), requested)) {
            logger.warn("[PlayerRouting] onPreConnect: {} is locked out of exclusive branch '{}' (DENIED)",
                    player.getUsername(), requested);
            player.sendMessage(Component.text("⚠ You chose a different path and cannot enter " + requested + "."));
            e.setResult(ServerPreConnectEvent.ServerResult.denied());
            return;
        }

        // Completion gates: groups named "completion_gate:<server>" list the story branches (any one) that open the server.
        List<String> gate = runtime.groupMembers("completion_gate:" + requested);
        if (!gate.isEmpty() && !hasBypass(player)
                && routingState.completedBranches(player.getUniqueId()).stream().noneMatch(done -> gate.contains(done))) {
            logger.warn("[PlayerRouting] onPreConnect: {} has not completed {} (DENIED entry to '{}')",
                    player.getUsername(), gate, requested);
            player.sendMessage(Component.text("⚠ " + requested + " opens to players who have completed the "
                    + String.join(" or ", gate) + " story."));
            e.setResult(ServerPreConnectEvent.ServerResult.denied());
            return;
        }

        String preferred = preferredFor(player.getUniqueId()).orElse(requested);
        
        logger.info("[PlayerRouting] onPreConnect: player={} requested={} preferred={}", 
                   player.getUsername(), requested, preferred);

        // Allow plugin-internal switches (auto-move on ready)
        if (runtime.stickyRouter().consumeInternalOnce(player.getUniqueId(), requested)) {
            logger.info("[PlayerRouting] onPreConnect: {} internal switch -> '{}' (ALLOWED)", player.getUsername(), requested);
            return;
        }

        boolean isAdmin = hasBypass(player); // routing.server
        logger.info("[PlayerRouting] onPreConnect: player={} isAdmin={}", player.getUsername(), isAdmin);

        // If an admin manually requests a server while a sticky-wait is in progress, cancel the sticky-wait.
        // Otherwise the background sticky router can keep attempting to connect and effectively override manual /server.
        if (isAdmin
                && !requested.equalsIgnoreCase(runtime.holdingServer())
                && runtime.stickyRouter().hasStickyState(player.getUniqueId())) {
            logger.info("[PlayerRouting] onPreConnect: {} admin manual request '{}' while sticky-wait active; cancelling sticky-wait",
                    player.getUsername(), requested);
            runtime.stickyRouter().cancelStickyWait(player.getUniqueId());
            Routing.fireStickyCancelled(player.getUniqueId());
        }

        // Block /server for non-admins (manual server switch = not preferred and not holding)
        if (!isAdmin && !requested.equalsIgnoreCase(preferred) && !requested.equalsIgnoreCase(runtime.holdingServer())) {
            logger.warn("[PlayerRouting] onPreConnect: {} manual switch DENIED (not admin)", player.getUsername());
            player.sendMessage(Component.text("⚠ Server switching is disabled."));
            e.setResult(ServerPreConnectEvent.ServerResult.denied());
            return;
        }

        // Enforce downward-only tier for non-admins (no jumping up)
        if (!isAdmin
                && !requested.equalsIgnoreCase(runtime.holdingServer())
                && !allowedTargetsDownward(player.getUniqueId()).contains(requested)) {

            logger.warn("[PlayerRouting] onPreConnect: {} tier restriction violated for '{}'", 
                       player.getUsername(), requested);
            
            // Pick highest allowed ONLINE; else deny
            var allowed = allowedTargetsDownward(player.getUniqueId());
            var chosen = chooseFirstOnline(allowed);
            if (chosen.isPresent()) {
                var srv = proxy.getServer(chosen.get()).orElse(null);
                if (srv != null) {
                    logger.info("[PlayerRouting] onPreConnect: redirecting {} from {} to {}", 
                               player.getUsername(), requested, chosen.get());
                    e.setResult(ServerPreConnectEvent.ServerResult.allowed(srv));
                    player.sendMessage(Component.text("➡ You don't have access to " + requested + " yet. Sending you to " + chosen.get() + "."));
                    return;
                }
            }
            logger.warn("[PlayerRouting] onPreConnect: no available servers for {}", player.getUsername());
            player.sendMessage(Component.text("⚠ No servers are available currently."));
            e.setResult(ServerPreConnectEvent.ServerResult.denied());
            return;
        }

        if (requested.equalsIgnoreCase(preferred)) {
            logger.info("[PlayerRouting] onPreConnect: {} heading to preferred '{}' (ALLOWED)", player.getUsername(), preferred);
            return; // let Velocity proceed
        }

        if (requested.equalsIgnoreCase(runtime.holdingServer())) {
            UUID playerId = player.getUniqueId();
            String origin = currentServerName(player).orElse(null);
            logger.info("[PlayerRouting] onPreConnect: {} heading to holding '{}', preferred='{}' (origin='{}')",
                    player.getUsername(), runtime.holdingServer(), preferred, origin);

            // A fresh external join is handled by AdmissionQueue/OfflineWaitManager after
            // ServerConnectedEvent confirms the player actually reached the lobby.
            if (Routing.isFreshArrival(playerId)) {
                logger.info("[PlayerRouting] onPreConnect: {} is a fresh admission; not creating sticky state",
                        player.getUsername());
                return;
            }

            // Existing backend -> lobby -> backend transfers keep the old sticky path.
            if (!runtime.stickyRouter().hasStickyState(playerId)) {
                logger.info("[PlayerRouting] onPreConnect: no existing sticky state, starting sticky toward '{}'", preferred);
                runtime.stickyRouter().beginStickyWait(playerId, preferred, origin);
            } else {
                logger.info("[PlayerRouting] onPreConnect: sticky state already exists for player {}, not recreating",
                        player.getUsername());
            }
            return;
        }

        // Admin manual or valid downward manual -> allow
        logger.info("[PlayerRouting] onPreConnect: {} manual request '{}'", player.getUsername(), requested);
    }

    /** Record visits; never record holding in visited or as last. */
    @Subscribe
    public void onConnected(ServerConnectedEvent e) {
        var p = e.getPlayer();
        UUID playerId = p.getUniqueId();
        var srv = e.getServer().getServerInfo().getName();

        logger.info("[PlayerRouting] onConnected: player={} server={}", p.getUsername(), srv);
        if (!srv.equalsIgnoreCase(runtime.holdingServer())) {
            runtime.stickyRouter().clearReturnEligibility(playerId);

            LobbyConfig cfg = runtime.currentConfig();
            boolean listed = cfg != null && cfg.returnServerOrder().stream().anyMatch(s -> s.equalsIgnoreCase(srv));
            routingState.recordSuccessfulConnection(playerId, srv, listed);
            logger.info("[PlayerRouting] onConnected: {} recorded successful gameplay connection '{}'", p.getUsername(), srv);
            return;
        }

        logger.info("[PlayerRouting] onConnected: {} connected to holding '{}', not recording as last/visited",
                p.getUsername(), srv);

        // A transfer that was pending when the player reached the holding server has already been resumed by the
        // Portals plugin (its ServerConnectedEvent handler runs at PostOrder.FIRST, before this NORMAL one), so it shows
        // up here as sticky state and skips admission below.
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        runtime.stickyRouter().clearInternalMark(playerId);
    }

    void cancelPortalTransfer(UUID playerId) {
        Routing.fireStickyCancelled(playerId);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (routingState != null) {
            routingState.close();
        }
    }

    /**
     * An admin command (/server, /forceserver) moves a player to a server. A server that is running is connected to at once. One that
     * is offline (stopped for being idle, or still starting) is not tried directly, because a failed connection would bounce the player
     * back to where they were: they wait in the lobby instead while it is woken and started, and are moved when it answers.
     */
    public void adminConnect(Player player, RegisteredServer target) {
        UUID id = player.getUniqueId();
        String name = target.getServerInfo().getName();
        String holding = runtime.holdingServer();
        runtime.stickyRouter().cancelStickyWait(id);
        cancelPortalTransfer(id);
        boolean offline = !name.equalsIgnoreCase(holding) && !OnDemandServers.isOnline(name);
        if (!offline) {
            runtime.stickyRouter().markInternalOnce(id, target.getServerInfo().getName());
            player.createConnectionRequest(target).fireAndForget();
            return;
        }
        String origin = currentServerName(player).orElse(null);
        player.sendMessage(Component.text("§e" + name + " is offline. Starting it; you will be moved when it is ready."));
        logger.info("[Admin] {} asked for offline server '{}'; waiting in '{}' while it starts", player.getUsername(), name, holding);
        Runnable wait = () -> runtime.stickyRouter().beginStickyWait(id, name, origin);
        if (holding.equalsIgnoreCase(origin)) {
            wait.run();
            return;
        }
        var lobby = proxy.getServer(holding);
        if (lobby.isEmpty()) {
            logger.error("[Admin] holding server '{}' is not registered; cannot wait for '{}'", holding, name);
            player.sendMessage(Component.text("⚠ The waiting lobby is not available."));
            return;
        }
        runtime.stickyRouter().markInternalOnce(id, lobby.get().getServerInfo().getName());
        player.createConnectionRequest(lobby.get()).connect().whenComplete((result, failure) -> {
            if (result != null && result.isSuccessful()) {
                wait.run();
            } else {
                logger.warn("[Admin] could not move {} to the waiting lobby: {}", player.getUsername(), failure == null ? "not successful" : failure.toString());
                player.sendMessage(Component.text("⚠ Could not reach the waiting lobby."));
            }
        });
    }

    /** Someone is waiting for this server. If it is managed on demand it is started (and kept running) until they are in. */
    public void requestServer(String name) {
        OnDemandServers.request(name);
    }

    /* =================== Helpers & config =================== */

    private boolean hasBypass(Player p) {
        return hasAuthorizationPermission(p, SERVER_PERMISSION);
    }

    boolean hasAuthorizationPermission(Player player, PermissionNode permission) {
        return ProxyAuthorization.hasOnCurrentServer(player, permission);
    }


    private Optional<String> preferredFor(UUID uuid) {
        String v = routingState.preferredServer(uuid).orElse(null);
        if (v != null && !v.equalsIgnoreCase(runtime.holdingServer())) return Optional.of(v);
        return Optional.ofNullable(firstDefault());
    }

    private String firstDefault() {
        var def = runtime.groupMembers("default_group");
        if (def == null || def.isEmpty()) return null;
        return def.get(0);
    }

    /** True when {@code requested} is in the {@code exclusive_branches} group and another one was visited. */
    private boolean lockedOutOfBranch(UUID who, String requested) {
        List<String> branches = runtime.groupMembers("exclusive_branches");
        // Only a branch server can be locked, so skip the database read for every other connect.
        if (branches.stream().noneMatch(b -> b.equalsIgnoreCase(requested))) return false;
        return com.silver.routing.session.BranchLock.lockedOut(branches, routingState.refreshVisitedServers(who), requested);
    }

    /** Highest index in default_group the player has visited; -1 if none. */
    private int maxVisitedIndex(UUID who) {
        var def = runtime.groupMembers("default_group");
        var seen = routingState.visitedServers(who);
        int max = -1;
        for (int i = 0; i < def.size(); i++) if (seen.contains(def.get(i))) max = i;
        return max;
    }

    /** Allowed targets in descending order: [tier, tier-1, ..., 0]. New players => [def[0]]  */
    private List<String> allowedTargetsDownward(UUID who) {
        var def = runtime.groupMembers("default_group");
        var seen = routingState.visitedServers(who);
        int max = maxVisitedIndex(who);
        
        logger.debug("[PlayerRouting] allowedTargetsDownward: uuid={} visited={} maxIndex={}", who, seen, max);
        
        if (def.isEmpty()) return List.of();
        if (max < 0) return List.of(def.get(0));
        var out = new ArrayList<String>(max + 1);
        for (int i = max; i >= 0; i--) out.add(def.get(i));
        
        logger.debug("[PlayerRouting] allowedTargetsDownward: returning {}", out);
        return out;
    }

    private Optional<String> chooseFirstOnline(List<String> names) {
        for (String name : names) {
            if (proxy.getServer(name).isEmpty()) continue;
            if (OnDemandServers.isOnline(name)) return Optional.of(name);
        }
        return Optional.empty();
    }

    void reloadConfig() throws IOException {
        loadConfig();
        OnDemandServers.reload();
        Routing.fireReload();
    }

    private LobbyConfig loadConfig() throws IOException {
        LobbyConfig config = configLoader.load();
        runtime.applyConfig(config, this::allowedTargetsDownward);
        return config;
    }

    private Optional<String> currentServerName(Player p) {
        return p.getCurrentServer().map(cs -> cs.getServerInfo().getName());
    }

    /** What other proxy plugins (Portals) may ask of the routing core; see {@link Routing}. */
    private final class RoutingBackend implements Routing.Backend {
        @Override
        public void beginStickyWait(UUID playerId, String target, String originServer) {
            runtime.stickyRouter().beginStickyWait(playerId, target, originServer);
        }

        @Override
        public void resumeStickyWait(UUID playerId, String target, String originServer) {
            Routing.fireStickyResumed(playerId);
            runtime.stickyRouter().beginStickyWait(playerId, target, originServer);
        }

        @Override
        public boolean hasStickyState(UUID playerId) {
            return runtime.stickyRouter().hasStickyState(playerId);
        }

        @Override
        public void cancelStickyWait(UUID playerId) {
            runtime.stickyRouter().cancelStickyWait(playerId);
        }

        @Override
        public void markInternalOnce(UUID playerId, String target) {
            runtime.stickyRouter().markInternalOnce(playerId, target);
        }

        @Override
        public String holdingServer() {
            return runtime == null ? null : runtime.holdingServer();
        }

        @Override
        public Optional<String> preferredServer(UUID playerId) {
            return preferredFor(playerId);
        }

        @Override
        public void setPreferredServer(UUID playerId, String server) {
            routingState.setPreferredServer(playerId, server);
        }

        @Override
        public Optional<Routing.StickyWaitInfo> stickyWait(UUID playerId) {
            return runtime.stickyRouter().stickyWait(playerId)
                    .map(wait -> new Routing.StickyWaitInfo(wait.target(), wait.sinceMs()));
        }

        @Override
        public void clearInternalMark(UUID playerId) {
            runtime.stickyRouter().clearInternalMark(playerId);
        }

        @Override
        public void clearReturnEligibility(UUID playerId) {
            runtime.stickyRouter().clearReturnEligibility(playerId);
        }

        @Override
        public String returnDestination(UUID playerId) {
            return computeReturnDestination(playerId);
        }

        @Override
        public long graceSec() {
            LobbyConfig cfg = runtime == null ? null : runtime.currentConfig();
            return cfg == null ? 0L : cfg.graceSec();
        }
    }
}
