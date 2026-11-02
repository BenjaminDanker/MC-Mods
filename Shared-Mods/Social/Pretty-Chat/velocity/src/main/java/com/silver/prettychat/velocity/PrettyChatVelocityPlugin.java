package com.silver.prettychat.velocity;

import com.google.inject.Inject;
import com.silver.prettychat.api.PrettyChatRenderer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(id = "prettychat", name = "Pretty Chat", version = "1.0.0", authors = {"SilverSphere"})
public final class PrettyChatVelocityPlugin implements PrettyChatRenderer {
    private final ProxyServer proxy;
    private final Logger logger;
    private final PrettyChatFormatter renderer = new PrettyChatFormatter();

    @Inject
    public PrettyChatVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Pretty Chat Velocity renderer API registered");
    }

    @Override
    public Component render(UUID playerId, String username, String message,
                            com.silver.prettychat.api.ChatKind kind) {
        return renderer.render(playerId, username, message, kind);
    }
}
