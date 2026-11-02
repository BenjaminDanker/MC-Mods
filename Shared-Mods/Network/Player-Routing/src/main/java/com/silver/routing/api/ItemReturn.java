package com.silver.routing.api;

import com.silver.routing.config.ReturnSpecial;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Removes the special items a player must give up for /return. Provided by the Portals plugin (Player Sync channel). */
@FunctionalInterface
public interface ItemReturn {
    CompletableFuture<ReturnRemoveResponse> requestItemReturn(Player player, List<ReturnSpecial> specials);
}
