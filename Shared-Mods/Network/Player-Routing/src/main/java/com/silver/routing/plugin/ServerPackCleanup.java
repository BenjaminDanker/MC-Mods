package com.silver.routing.plugin;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * A server's resource pack stays on the player's game after they move to another server: since 1.20.3 the game keeps
 * server packs through a server switch, and the proxy never removes them. This remembers which server sent each pack
 * and, once a switch is done, removes the packs the new server did not send (the new server's own packs arrive while
 * it is joined, before this runs). A pack the game already has is not downloaded again; it is kept by its hash.
 */
final class ServerPackCleanup {
    private final Logger logger;
    /** player -> (pack id -> the server that sent it) */
    private final Map<UUID, Map<UUID, String>> sentBy = new ConcurrentHashMap<>();

    ServerPackCleanup(Logger logger) {
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPackSent(ServerResourcePackSendEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        ResourcePackInfo pack = event.getProvidedResourcePack();
        if (pack == null || pack.getId() == null) {
            return;
        }
        sentBy.computeIfAbsent(event.getServerConnection().getPlayer().getUniqueId(), id -> new ConcurrentHashMap<>())
            .put(pack.getId(), event.getServerConnection().getServerInfo().getName());
    }

    @Subscribe
    public void onSwitched(ServerPostConnectEvent event) {
        if (event.getPreviousServer() == null) {
            return; // the first server of this session: nothing from before
        }
        Player player = event.getPlayer();
        String now = player.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse(null);
        Map<UUID, String> packs = sentBy.get(player.getUniqueId());
        if (now == null || packs == null || packs.isEmpty()) {
            return;
        }
        List<UUID> stale = new ArrayList<>();
        packs.forEach((pack, server) -> {
            if (!server.equals(now)) {
                stale.add(pack);
            }
        });
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(packs::remove);
        player.removeResourcePacks(stale.get(0), stale.subList(1, stale.size()).toArray(UUID[]::new));
        logger.info("[PlayerRouting] {} moved to {}: removed {} resource pack(s) from the previous server",
            player.getUsername(), now, stale.size());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sentBy.remove(event.getPlayer().getUniqueId());
    }
}
