package com.silver.authorization.velocity;

import com.silver.authorization.AuthorizationSubject;
import com.silver.authorization.PermissionNode;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.ServerId;
import com.velocitypowered.api.proxy.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Static, thread-safe entry point for other proxy plugins. Every query fails closed (returns false) while the
 * Network Authorization plugin has not created its service.
 */
public final class ProxyAuthorization {
    private static final PermissionNode SERVER_PERMISSION = PermissionNodes.WAKEUPLOBBY_SERVER;
    private static volatile AuthorizationService service;

    private ProxyAuthorization() { }

    static void install(AuthorizationService instance) { service = instance; }

    static void uninstall() { service = null; }

    /** True once the service object exists (it may still lack usable state, in which case decisions are denied). */
    public static boolean ready() { return service != null; }

    /** Permission on a named server; false when not ready, the name is not a server id or the server is not enabled. */
    public static boolean has(UUID player, PermissionNode node, String serverName) {
        AuthorizationService authorization = service;
        if (authorization == null || player == null || node == null || serverName == null) return false;
        ServerId server;
        try { server = ServerId.of(serverName); } catch (IllegalArgumentException notAServerId) { return false; }
        return authorization.currentState().enabledServers().contains(server)
                && authorization.has(AuthorizationSubject.player(player), node, server);
    }

    /** Permission on the server the player is currently connected to (false when on none). */
    public static boolean hasOnCurrentServer(Player player, PermissionNode node) {
        if (player == null) return false;
        Optional<String> current = player.getCurrentServer().map(connection -> connection.getServerInfo().getName());
        return current.isPresent() && has(player.getUniqueId(), node, current.get());
    }

    /**
     * Admin on a server, or on any server when {@code serverNameOrNull} is null. Doesn't need the player to be on a
     * server already (at login and on their first connect they aren't).
     */
    public static boolean isAdminOn(UUID player, String serverNameOrNull) {
        AuthorizationService authorization = service;
        if (authorization == null || player == null) return false;
        Set<ServerId> enabled = authorization.currentState().enabledServers();
        AuthorizationSubject subject = AuthorizationSubject.player(player);
        if (serverNameOrNull == null) {
            return enabled.stream().anyMatch(server -> authorization.has(subject, SERVER_PERMISSION, server));
        }
        try {
            ServerId server = ServerId.of(serverNameOrNull);
            return enabled.contains(server) && authorization.has(subject, SERVER_PERMISSION, server);
        } catch (IllegalArgumentException notAServerId) {
            return false;
        }
    }

    public static boolean isAdminOn(Player player, String serverNameOrNull) {
        return player != null && isAdminOn(player.getUniqueId(), serverNameOrNull);
    }

    /** {@code wakeuplobby.server} on the player's current server (the proxy's "bypass"). */
    public static boolean hasBypass(Player player) {
        return hasOnCurrentServer(player, SERVER_PERMISSION);
    }
}
