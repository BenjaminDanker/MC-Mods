package com.silver.networkaccess.plugin;

import com.silver.authorization.velocity.ProxyAuthorization;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

/**
 * Keeps players out of a server whose own whitelist is on and does not list them. Admins of that server get in anyway
 * (as server operators do with the vanilla whitelist). Runs late, so it judges where routing finally sends the player.
 */
public final class ServerWhitelistCheck {
    private final Whitelists whitelists;
    private final Logger logger;

    public ServerWhitelistCheck(Whitelists whitelists, Logger logger) {
        this.whitelists = whitelists;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getResult().getServer().orElse(null);
        if (target == null) {
            return;
        }
        Player player = event.getPlayer();
        String server = target.getServerInfo().getName();
        if (whitelists.allowedOn(server, player.getUniqueId()) || ProxyAuthorization.isAdminOn(player, server)) {
            return;
        }
        logger.info("[NetworkAccess] {} kept out of {} (not on its whitelist)", player.getUsername(), server);
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.sendMessage(Component.text("You are not whitelisted on " + server + ".", NamedTextColor.RED));
    }
}
