package com.silver.routing.api;

import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What the routing plugin (player-routing) offers to other proxy plugins (static, like ProxyAuthorization), and the hooks
 * they can register. This class lives only in the player-routing jar; users compile against it and declare a
 * {@code @Dependency(id = "player-routing")}.
 */
public final class Routing {
    /** Implemented by the routing plugin. */
    public interface Backend {
        void beginStickyWait(UUID playerId, String target, String originServer);

        /** Like beginStickyWait, for a player that just arrived in the holding server: also drops their admission state. */
        void resumeStickyWait(UUID playerId, String target, String originServer);

        boolean hasStickyState(UUID playerId);

        void cancelStickyWait(UUID playerId);

        void markInternalOnce(UUID playerId, String target);

        String holdingServer();

        /** The server the player is heading for: their saved preferred server, else the first of the default group. */
        Optional<String> preferredServer(UUID playerId);

        /** Saves the player's preferred server (the single source of truth for where they are routed). */
        void setPreferredServer(UUID playerId, String server);

        /** The player's current sticky wait, if any. */
        Optional<StickyWaitInfo> stickyWait(UUID playerId);

        void clearInternalMark(UUID playerId);

        void clearReturnEligibility(UUID playerId);

        /** Where /return would send this player. */
        String returnDestination(UUID playerId);

        /** Seconds a wait lasts before /return opens. */
        long graceSec();
    }

    /** A running sticky wait: the server being waited for and when the wait began (epoch ms). */
    public record StickyWaitInfo(String target, long sinceMs) {
    }

    private static volatile Backend backend;
    private static final List<Consumer<UUID>> STICKY_CANCELLED = new CopyOnWriteArrayList<>();
    private static final List<Runnable> RELOAD = new CopyOnWriteArrayList<>();
    private static volatile StickyConnectHook stickyConnectHook;
    private static volatile ItemReturn itemReturn;
    private static final List<Consumer<UUID>> STICKY_RESUMED = new CopyOnWriteArrayList<>();
    private static volatile Predicate<UUID> freshArrival = id -> false;
    private static volatile Predicate<Player> offlineReturn = player -> false;

    private Routing() {
    }

    /* ---------- called by other plugins ---------- */

    /** Keep the player in the holding server and move them to {@code target} once it is up ("move when ready"). */
    public static void beginStickyWait(UUID playerId, String target, String originServer) {
        require().beginStickyWait(playerId, target, originServer);
    }

    /** {@link Backend#resumeStickyWait}. */
    public static void resumeStickyWait(UUID playerId, String target, String originServer) {
        require().resumeStickyWait(playerId, target, originServer);
    }

    public static boolean hasStickyState(UUID playerId) {
        return require().hasStickyState(playerId);
    }

    public static void cancelStickyWait(UUID playerId) {
        require().cancelStickyWait(playerId);
    }

    /** The player's next connect to {@code target} is plugin-internal (skips the manual-switch and tier checks). */
    public static void markInternalOnce(UUID playerId, String target) {
        require().markInternalOnce(playerId, target);
    }

    /** {@link Backend#preferredServer}. */
    public static Optional<String> preferredServer(UUID playerId) {
        return require().preferredServer(playerId);
    }

    /** {@link Backend#setPreferredServer}. */
    public static void setPreferredServer(UUID playerId, String server) {
        require().setPreferredServer(playerId, server);
    }

    /** {@link Backend#stickyWait}. */
    public static Optional<StickyWaitInfo> stickyWait(UUID playerId) {
        return require().stickyWait(playerId);
    }

    public static void clearInternalMark(UUID playerId) {
        require().clearInternalMark(playerId);
    }

    public static void clearReturnEligibility(UUID playerId) {
        require().clearReturnEligibility(playerId);
    }

    /** {@link Backend#returnDestination}. */
    public static String returnDestination(UUID playerId) {
        return require().returnDestination(playerId);
    }

    /** {@link Backend#graceSec}. */
    public static long graceSec() {
        return require().graceSec();
    }

    /** Name of the holding (waiting lobby) server; null before the routing plugin has started. */
    public static String holdingServer() {
        Backend current = backend;
        return current == null ? null : current.holdingServer();
    }

    /** Runs when an admin or /return cancels a player's wait, with that player's id. */
    public static void onStickyCancelled(Consumer<UUID> listener) {
        STICKY_CANCELLED.add(listener);
    }

    /** Runs after the routing plugin reloads its config (/wakeuplobby reload). */
    public static void onReload(Runnable listener) {
        RELOAD.add(listener);
    }

    /** Runs when a player arrives in the holding server with a pending transfer (their admission state is dropped). */
    public static void onStickyResumed(Consumer<UUID> listener) {
        STICKY_RESUMED.add(listener);
    }

    /**
     * Tells the routing plugin which players are fresh arrivals being placed by the waiting lobby; those are not given a
     * sticky wait when they reach the holding server.
     */
    public static void setFreshArrivalCheck(Predicate<UUID> check) {
        freshArrival = check == null ? id -> false : check;
    }

    /** Handles /return for a player waiting on an offline server; returns true when it took the command. */
    public static void setOfflineReturnHandler(Predicate<Player> handler) {
        offlineReturn = handler == null ? player -> false : handler;
    }

    public static void setStickyConnectHook(StickyConnectHook hook) {
        stickyConnectHook = hook;
    }

    public static void setItemReturn(ItemReturn provider) {
        itemReturn = provider;
    }

    /* ---------- called by the routing plugin ---------- */

    public static void install(Backend implementation) {
        backend = implementation;
    }

    public static void fireStickyCancelled(UUID playerId) {
        for (Consumer<UUID> listener : STICKY_CANCELLED) {
            try {
                listener.accept(playerId);
            } catch (RuntimeException ignored) {
                // one listener must not stop the others or the caller
            }
        }
    }

    public static void fireStickyResumed(UUID playerId) {
        for (Consumer<UUID> listener : STICKY_RESUMED) {
            try {
                listener.accept(playerId);
            } catch (RuntimeException ignored) {
                // see above
            }
        }
    }

    public static boolean isFreshArrival(UUID playerId) {
        return freshArrival.test(playerId);
    }

    public static boolean handleOfflineReturn(Player player) {
        return offlineReturn.test(player);
    }

    public static void fireReload() {
        for (Runnable listener : RELOAD) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // see above
            }
        }
    }

    public static Optional<StickyConnectHook> stickyConnectHook() {
        return Optional.ofNullable(stickyConnectHook);
    }

    public static Optional<ItemReturn> itemReturn() {
        return Optional.ofNullable(itemReturn);
    }

    private static Backend require() {
        Backend current = backend;
        if (current == null) {
            throw new IllegalStateException("The routing plugin (player-routing) has not started yet");
        }
        return current;
    }
}
