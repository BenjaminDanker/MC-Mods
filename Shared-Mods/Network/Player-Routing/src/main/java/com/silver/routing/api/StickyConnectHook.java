package com.silver.routing.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Called right before the routing plugin connects a waiting player to the server they were waiting for. The Portals
 * plugin uses it to claim a pending transfer. The completed value is run if that connection then fails or the player
 * is gone (it puts the claim back).
 */
@FunctionalInterface
public interface StickyConnectHook {
    CompletableFuture<Runnable> beforeConnect(UUID playerId, String target);
}
