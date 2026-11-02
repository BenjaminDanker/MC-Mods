package com.silver.waiting.plugin;

import com.google.inject.Inject;
import com.silver.authorization.PermissionNode;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.velocity.ProxyAuthorization;
import com.silver.ondemand.OnDemandServers;
import com.silver.routing.api.Routing;
import com.silver.waiting.admission.AdmissionQueue;
import com.silver.waiting.admission.OfflineWaitManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The waiting lobby: every fresh join (and every player kicked from a backend) is held in the holding server, queued by
 * tier, and released to their server when it is up and accepting. Players whose server is offline wait outside the
 * queue (the server is started on demand), with a boss bar showing the place in line or the wait time.
 *
 * <p>Routing (config, sticky waits, visits, policy gates) lives in the {@code player-routing} plugin and is used only
 * through {@link Routing}; start/stop and admission capacity come from {@code on-demand-servers}.
 */
@Plugin(id = "waiting-lobby", name = "Waiting Lobby", version = "1.0.0",
        dependencies = {@Dependency(id = "player-routing"), @Dependency(id = "on-demand-servers"),
                @Dependency(id = "network-authorization")})
public class WaitingLobbyPlugin {
    private static final PermissionNode SERVER_PERMISSION = PermissionNodes.WAKEUPLOBBY_SERVER;
    private static final PermissionNode BIG_VIP_PERMISSION = PermissionNodes.WAKEUPLOBBY_QUEUE_BIGVIP;
    private static final PermissionNode VIP_PERMISSION = PermissionNodes.WAKEUPLOBBY_QUEUE_VIP;

    private final ProxyServer proxy;
    private final Logger logger;

    private final AdmissionQueue admissionQueue = new AdmissionQueue();
    private final OfflineWaitManager offlineWaitManager = new OfflineWaitManager();
    private final Set<UUID> freshAdmissions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean admissionDispatchInFlight = new AtomicBoolean(false);

    /** Players waiting in the lobby for their server to start: a boss bar with the elapsed time, until they move on. */
    private final Map<UUID, BossBar> waitingBars = new ConcurrentHashMap<>();

    @Inject
    public WaitingLobbyPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            // Routing and on-demand-servers are already loaded (declared @Dependency); hand them what only this plugin knows.
            Routing.setFreshArrivalCheck(freshAdmissions::contains);
            Routing.setOfflineReturnHandler(this::handleOfflineReturn);
            Routing.onStickyResumed(this::forgetAdmission);
            OnDemandServers.setWaitingServers(() -> offlineWaitManager.snapshot().stream()
                    .map(entry -> Routing.preferredServer(entry.playerId()))
                    .flatMap(Optional::stream)
                    .toList());

            proxy.getScheduler().buildTask(this, this::showWaitingStatus)
                    .repeat(Duration.ofSeconds(1))
                    .schedule();

            proxy.getScheduler().buildTask(this, this::promoteOfflineWaiters)
                    .delay(Duration.ofSeconds(1))
                    .repeat(Duration.ofSeconds(1))
                    .schedule();

            proxy.getScheduler().buildTask(this, this::processAdmissionQueue)
                    .delay(Duration.ofMillis(2500))
                    .repeat(Duration.ofMillis(2500))
                    .schedule();

            logger.info("[WaitingLobby] Loaded. Holding={}", Routing.holdingServer());
        } catch (Exception ex) {
            logger.error("Failed to initialize Waiting Lobby", ex);
        }
    }

    /**
     * Every fresh external connection starts in the holding lobby.
     * The admission system decides when that player may enter their server.
     */
    @Subscribe
    public void onChoose(PlayerChooseInitialServerEvent e) {
        var p = e.getPlayer();
        UUID playerId = p.getUniqueId();
        freshAdmissions.add(playerId);

        String holding = Routing.holdingServer();
        String preferred = Routing.preferredServer(playerId).orElse(null);
        logger.info("[WaitingLobby] onChoose: fresh player={} preferred={}", p.getUsername(), preferred);

        var holdReg = holding == null ? Optional.<com.velocitypowered.api.proxy.server.RegisteredServer>empty()
                : proxy.getServer(holding);
        if (holdReg.isEmpty()) {
            logger.error("[WaitingLobby] Holding server '{}' is not registered", holding);
            return;
        }

        e.setInitialServer(holdReg.get());
    }

    /**
     * A backend going away does not create a new proxy connection, so it never
     * reaches {@link #onChoose(PlayerChooseInitialServerEvent)}. Redirect the
     * player through the holding server and mark this as an admission flow so
     * {@link #onConnected(ServerConnectedEvent)} puts them into the queue (or
     * the offline waiting list) just like a newly connected player.
     */
    @Subscribe
    public void onKickedFromBackend(KickedFromServerEvent event) {
        String holdingServer = Routing.holdingServer();
        if (holdingServer == null) {
            return;
        }

        Player player = event.getPlayer();
        String source = event.getServer().getServerInfo().getName();

        // Do not turn a failed holding-lobby connection into a redirect loop.
        if (source.equalsIgnoreCase(holdingServer)) {
            return;
        }

        var holding = proxy.getServer(holdingServer);
        if (holding.isEmpty()) {
            logger.error("[Admission] Cannot recover {} from backend '{}' because holding server '{}' is not registered",
                    player.getUsername(), source, holdingServer);
            return;
        }

        UUID playerId = player.getUniqueId();
        // Cancel any previous transition so it cannot compete with admission.
        Routing.cancelStickyWait(playerId);
        Routing.clearReturnEligibility(playerId);
        admissionQueue.remove(playerId);
        offlineWaitManager.remove(playerId);
        freshAdmissions.add(playerId);

        // A kick from an already connected backend is a strong signal that it
        // cannot accept this player right now. The next health refresh can
        // promote the player immediately if that was only a transient kick.
        OnDemandServers.markOffline(source);

        logger.info("[Admission] backend '{}' disconnected {}; redirecting to holding '{}' for admission",
                source, player.getUsername(), holdingServer);
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(
                holding.get(),
                Component.text("⚠ " + source + " is unavailable. Sending you to the waiting lobby…")
        ));
    }

    @Subscribe
    public void onConnected(ServerConnectedEvent e) {
        var p = e.getPlayer();
        UUID playerId = p.getUniqueId();
        String srv = e.getServer().getServerInfo().getName();
        String holding = Routing.holdingServer();

        if (holding == null || !srv.equalsIgnoreCase(holding)) {
            // Successful entry into a backend means this player is no longer admission-waiting.
            forgetAdmission(playerId);
            return;
        }

        // A transfer that was pending when the player reached the holding server has already been resumed by the
        // Portals plugin (its ServerConnectedEvent handler runs at PostOrder.FIRST, before this NORMAL one), so it shows
        // up here as sticky state and skips admission below.

        // Backend -> lobby -> backend transit already has StickyState and never enters
        // fresh-player admission.
        if (Routing.hasStickyState(playerId)) {
            logger.info("[Admission] {} is internal transit; admission queue bypassed", p.getUsername());
            return;
        }

        // A player may remain in the lobby for a while. Do not enqueue them twice if
        // another ServerConnectedEvent is observed.
        if (admissionQueue.contains(playerId) || offlineWaitManager.contains(playerId)) {
            return;
        }

        if (!freshAdmissions.remove(playerId)) {
            logger.debug("[Admission] {} reached holding without fresh-admission state; leaving routing unchanged",
                    p.getUsername());
            return;
        }

        placeFreshPlayer(p);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        forgetAdmission(event.getPlayer().getUniqueId());
    }

    private void forgetAdmission(UUID playerId) {
        freshAdmissions.remove(playerId);
        admissionQueue.remove(playerId);
        offlineWaitManager.remove(playerId);
    }

    private void showWaitingStatus() {
        String holding = Routing.holdingServer();
        if (holding == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<UUID> still = new HashSet<>();
        for (Player player : proxy.getAllPlayers()) {
            UUID id = player.getUniqueId();
            String current = currentServerName(player).orElse(null);
            if (current == null || !current.equalsIgnoreCase(holding)) {
                continue;
            }
            String target = Routing.preferredServer(id).orElse("your server");
            String text;
            float progress;
            Optional<Routing.StickyWaitInfo> sticky = Routing.stickyWait(id);
            long waitingSince = offlineWaitManager.contains(id) ? offlineWaitManager.waitingSinceMs(id)
                    : sticky.map(Routing.StickyWaitInfo::sinceMs).orElse(-1L);
            if (sticky.isPresent()) {
                target = sticky.get().target();
            }
            if (waitingSince >= 0) {
                long seconds = Math.max(0, (now - waitingSince) / 1000);
                boolean starting = OnDemandServers.manages(target);
                boolean busy = starting && OnDemandServers.waitingForRoom(target);
                text = (busy ? "Waking up " + target + "  (the host is busy, hang on)" : starting ? "Waking up " + target : target + " is offline, waiting for it")
                        + "  " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
                // Starting a server takes about a minute; the bar fills over that and then waits full.
                progress = starting && !busy ? Math.min(1F, seconds / 70F) : 1F;
            } else if (admissionQueue.contains(id)) {
                int position = admissionQueue.position(id);
                boolean busy = !OnDemandServers.isAdmissionOpen() || !OnDemandServers.isAccepting(target);
                text = "In line for " + target + ": #" + position + (busy ? "  (the host is busy, hang on)" : "");
                progress = 1F;
            } else {
                continue;
            }
            still.add(id);
            Component name = Component.text(text, NamedTextColor.YELLOW);
            BossBar bar = waitingBars.computeIfAbsent(id, key -> {
                BossBar made = BossBar.bossBar(name, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                player.showBossBar(made);
                return made;
            });
            bar.name(name);
            bar.progress(progress);
        }
        waitingBars.entrySet().removeIf(entry -> {
            if (still.contains(entry.getKey())) {
                return false;
            }
            proxy.getPlayer(entry.getKey()).ifPresent(player -> player.hideBossBar(entry.getValue()));
            return true;
        });
    }

    private void placeFreshPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        String target = Routing.preferredServer(playerId).orElse(null);
        if (target == null || target.isBlank()) {
            player.sendMessage(Component.text("⚠ No destination server is configured."));
            return;
        }

        if (OnDemandServers.isOnline(target)) {
            enqueueAdmission(player);
            return;
        }

        offlineWaitManager.add(playerId);
        if (OnDemandServers.manages(target)) {
            OnDemandServers.request(target);
            logger.info("[Admission] {} is waiting for '{}' to start", player.getUsername(), target);
            return;
        }
        logger.info("[Admission] {} waiting outside queue because target '{}' is offline",
                player.getUsername(), target);
    }

    private void enqueueAdmission(Player player) {
        UUID playerId = player.getUniqueId();
        AdmissionQueue.Tier tier = queueTier(player);
        if (!admissionQueue.enqueue(playerId, tier)) {
            return;
        }

        int position = admissionQueue.position(playerId);
        // The lobby boss bar shows the place in line; no chat line.
        logger.info("[Admission] queued {} tier={} position={}", player.getUsername(), tier, position);
    }

    private AdmissionQueue.Tier queueTier(Player player) {
        if (has(player, SERVER_PERMISSION)) {
            return AdmissionQueue.Tier.ADMIN;
        }
        if (has(player, BIG_VIP_PERMISSION)) {
            return AdmissionQueue.Tier.BIG_VIP;
        }
        if (has(player, VIP_PERMISSION)) {
            return AdmissionQueue.Tier.VIP;
        }
        return AdmissionQueue.Tier.NORMAL;
    }

    private static boolean has(Player player, PermissionNode permission) {
        return ProxyAuthorization.hasOnCurrentServer(player, permission);
    }

    private void promoteOfflineWaiters() {
        String holding = Routing.holdingServer();
        if (holding == null) {
            return;
        }

        for (OfflineWaitManager.Entry entry : offlineWaitManager.snapshot()) {
            UUID playerId = entry.playerId();
            Optional<Player> playerOpt = proxy.getPlayer(playerId);
            if (playerOpt.isEmpty()) {
                offlineWaitManager.remove(playerId);
                continue;
            }

            Player player = playerOpt.get();
            String current = currentServerName(player).orElse(null);
            if (current == null || !current.equalsIgnoreCase(holding)) {
                offlineWaitManager.remove(playerId);
                continue;
            }

            // If this somehow became an internal transit, sticky routing owns it.
            if (Routing.hasStickyState(playerId)) {
                offlineWaitManager.remove(playerId);
                continue;
            }

            String target = Routing.preferredServer(playerId).orElse(null);
            if (target == null || !OnDemandServers.isOnline(target)) {
                continue;
            }

            offlineWaitManager.remove(playerId);
            enqueueAdmission(player);
        }
    }

    private void processAdmissionQueue() {
        String holding = Routing.holdingServer();
        if (holding == null) {
            return;
        }
        if (!OnDemandServers.isAdmissionOpen() || !admissionDispatchInFlight.compareAndSet(false, true)) {
            return;
        }

        List<AdmissionQueue.Entry> deferred = new ArrayList<>();
        try {
            while (true) {
                AdmissionQueue.Entry entry = admissionQueue.poll();
                if (entry == null) {
                    admissionDispatchInFlight.set(false);
                    return;
                }

                Optional<Player> playerOpt = proxy.getPlayer(entry.playerId());
                if (playerOpt.isEmpty()) {
                    continue;
                }

                Player player = playerOpt.get();
                String current = currentServerName(player).orElse(null);
                if (current == null || !current.equalsIgnoreCase(holding)) {
                    continue;
                }

                if (Routing.hasStickyState(entry.playerId())) {
                    continue;
                }

                String target = Routing.preferredServer(entry.playerId()).orElse(null);
                if (target == null || target.isBlank()) {
                    player.sendMessage(Component.text("⚠ No destination server is configured."));
                    continue;
                }

                if (!OnDemandServers.isOnline(target)) {
                    offlineWaitManager.add(entry.playerId());
                    continue;
                }

                var serverOpt = proxy.getServer(target);
                if (serverOpt.isEmpty()) {
                    OnDemandServers.markOffline(target);
                    offlineWaitManager.add(entry.playerId());
                    continue;
                }

                if (!OnDemandServers.isAccepting(target)) {
                    deferred.add(entry); // the server is lagging: keep this player's place and try again next round
                    continue;
                }

                logger.info("[Admission] releasing {} -> {} tier={}",
                        player.getUsername(), target, entry.tier());

                Routing.markInternalOnce(entry.playerId(), serverOpt.get().getServerInfo().getName());
                player.createConnectionRequest(serverOpt.get()).connect().whenComplete((result, error) -> {
                    admissionDispatchInFlight.set(false);

                    if (error == null && result != null && result.isSuccessful()) {
                        return;
                    }

                    // Clear an internal marker if ServerPreConnectEvent never consumed it.
                    Routing.clearInternalMark(entry.playerId());
                    OnDemandServers.markOffline(target);

                    Optional<Player> stillOnline = proxy.getPlayer(entry.playerId());
                    if (stillOnline.isPresent()) {
                        Player waitingPlayer = stillOnline.get();
                        String waitingCurrent = currentServerName(waitingPlayer).orElse(null);
                        if (waitingCurrent != null && waitingCurrent.equalsIgnoreCase(holding)) {
                            offlineWaitManager.add(entry.playerId());
                        }
                    }
                });
                return;
            }
        } catch (Throwable t) {
            admissionDispatchInFlight.set(false);
            logger.warn("[Admission] queue processing failed: {}", t.toString());
        } finally {
            deferred.forEach(admissionQueue::requeue);
        }
    }

    /** /return for a player waiting on an offline server: opens after the grace period, then re-enters the normal queue. */
    private boolean handleOfflineReturn(Player player) {
        UUID playerId = player.getUniqueId();
        if (!offlineWaitManager.contains(playerId)) {
            return false;
        }
        if (!isOfflineReturnEligible(playerId)) {
            long seconds = offlineReturnSecondsRemaining(playerId);
            player.sendMessage(Component.text(
                    "⚠ /return is available in " + seconds + "s if the server is still offline."));
            return true;
        }
        queueOfflineReturn(player);
        return true;
    }

    boolean isOfflineReturnEligible(UUID playerId) {
        long waitingSince = offlineWaitManager.waitingSinceMs(playerId);
        if (waitingSince < 0) {
            return false;
        }
        long graceMs = Routing.graceSec() * 1000L;
        return System.currentTimeMillis() - waitingSince >= graceMs;
    }

    long offlineReturnSecondsRemaining(UUID playerId) {
        long waitingSince = offlineWaitManager.waitingSinceMs(playerId);
        if (waitingSince < 0) {
            return 0L;
        }
        long graceMs = Routing.graceSec() * 1000L;
        long remainingMs = graceMs - (System.currentTimeMillis() - waitingSince);
        return Math.max(0L, (remainingMs + 999L) / 1000L);
    }

    boolean queueOfflineReturn(Player player) {
        UUID playerId = player.getUniqueId();
        if (!offlineWaitManager.contains(playerId)) {
            return false;
        }

        String dest = Routing.returnDestination(playerId);
        if (dest == null || dest.isBlank()) {
            player.sendMessage(Component.text("⚠ No return destination is configured."));
            return false;
        }

        if (!OnDemandServers.isOnline(dest)) {
            player.sendMessage(Component.text("⚠ Return destination " + dest + " is currently offline."));
            return false;
        }

        // This is the player's chosen routing state, not queue-owned destination state.
        // Persist it so the routing plugin's preferred server stays the single source of truth.
        Routing.setPreferredServer(playerId, dest);
        Routing.fireStickyCancelled(playerId);

        offlineWaitManager.remove(playerId);
        enqueueAdmission(player);
        return true;
    }

    private static Optional<String> currentServerName(Player p) {
        return p.getCurrentServer().map(cs -> cs.getServerInfo().getName());
    }
}
