package com.silver.resonantmessage.velocity;

import com.google.inject.Inject;
import com.silver.resonantmessage.common.ResonanceProtocol;
import com.silver.resonantmessage.common.ResonanceProtocol.Acknowledgement;
import com.silver.resonantmessage.common.ResonanceProtocol.Request;
import com.silver.prettychat.api.ChatKind;
import com.silver.prettychat.api.PrettyChatRenderer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(id = "resonantmessage", name = "Resonant Message", version = "1.0.0",
        authors = {"SilverSphere"}, dependencies = {@Dependency(id = "prettychat")})
public final class ResonantMessageVelocityPlugin {
    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("resonant:message_v1");
    private static final Duration MAX_REQUEST_AGE = Duration.ofSeconds(30);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private Map<String, byte[]> backendKeys = Map.of();
    private ReplayGuard replayGuard;
    private PrettyChatRenderer chatRenderer;

    @Inject
    public ResonantMessageVelocityPlugin(ProxyServer proxy, Logger logger,
                                          @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        chatRenderer = proxy.getPluginManager().getPlugin("prettychat")
                .flatMap(PluginContainer::getInstance)
                .filter(PrettyChatRenderer.class::isInstance)
                .map(PrettyChatRenderer.class::cast)
                .orElseThrow(() -> new IllegalStateException("Pretty Chat renderer API is unavailable"));
        backendKeys = VelocityConfig.loadBackendKeys(dataDirectory, logger);
        try {
            replayGuard = new ReplayGuard(dataDirectory);
        } catch (IOException failure) {
            logger.error("[ResonantMessage] Replay journal unavailable; messages fail closed", failure);
            replayGuard = null;
        }
        logger.info("[ResonantMessage] Velocity component ready; configured backend keys={}", backendKeys.size());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        handleRequest(event);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        proxy.getChannelRegistrar().unregister(CHANNEL);
    }

    private void handleRequest(PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection backend)
                || !(event.getTarget() instanceof Player player)
                || replayGuard == null) return;

        String trustedBackend = backend.getServerInfo().getName().toLowerCase(java.util.Locale.ROOT);
        Optional<String> currentBackend = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().toLowerCase(java.util.Locale.ROOT));
        if (!trustedBackend.equals(currentBackend.orElse(""))) return;

        byte[] key = backendKeys.get(trustedBackend);
        if (key == null) return;

        try {
            Request request = ResonanceProtocol.decodeRequest(event.getData());
            long now = System.currentTimeMillis();
            long age = now - request.issuedAtEpochMillis();
            if (request.protocolVersion() != ResonanceProtocol.VERSION
                    || !request.backendId().equals(trustedBackend)
                    || !request.playerId().equals(player.getUniqueId())
                    || !ResonanceProtocol.validBackendId(request.backendId())
                    || Math.abs(age) > MAX_REQUEST_AGE.toMillis()
                    || !ResonanceProtocol.validMessage(request.message())
                    || !ResonanceProtocol.verify(request, key)) {
                logger.debug("[ResonantMessage] Rejected invalid request from backend={}", trustedBackend);
                return;
            }
            if (!replayGuard.markIfNew(trustedBackend, request.backendEpoch(), request.nonce(), now)) {
                logger.warn("[ResonantMessage] Rejected replayed request backend={} player={}",
                        trustedBackend, player.getUniqueId());
                return;
            }

            Component rendered = chatRenderer.render(player.getUniqueId(), player.getUsername(),
                    request.message(), ChatKind.RESONANT);
            List<Player> recipients = List.copyOf(proxy.getAllPlayers());
            boolean senderIncluded = recipients.stream().anyMatch(recipient ->
                    recipient.getUniqueId().equals(player.getUniqueId()));
            if (!senderIncluded) return;
            recipients.forEach(recipient -> recipient.sendMessage(rendered));

            Acknowledgement acknowledgement = ResonanceProtocol.sign(new Acknowledgement(
                    ResonanceProtocol.VERSION, trustedBackend, player.getUniqueId(),
                    request.backendEpoch(), request.nonce(), true, ""), key);
            if (!backend.sendPluginMessage(CHANNEL, ResonanceProtocol.encode(acknowledgement))) {
                logger.warn("[ResonantMessage] Broadcast accepted but acknowledgement could not be sent to backend={} player={}",
                        trustedBackend, player.getUniqueId());
            } else {
                logger.debug("[ResonantMessage] Accepted broadcast backend={} player={} recipients={}",
                        trustedBackend, player.getUniqueId(), recipients.size());
            }
        } catch (RuntimeException | IOException failure) {
            logger.warn("[ResonantMessage] Rejected malformed or unauthenticated request: {}", failure.toString());
        }
    }

}
