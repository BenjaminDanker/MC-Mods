package com.silver.aipets.velocity;

import com.google.inject.Inject;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Velocity is the sole network-presence source; backend connection events are not subscribed. */
@Plugin(id = "petcompanionpresence", name = "Pet Companion Presence", version = "0.1.0")
public final class PetCompanionVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private PetNetworkPresenceReporter reporter;

    @Inject
    public PetCompanionVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            Optional<PetPresenceClientConfig> configured =
                    PetPresenceClientConfig.fromEnvironment(System.getenv());
            if (configured.isEmpty()) {
                logger.warn(StructuredPetEvent.operation("velocity_presence_config")
                        .outcome("disabled").toJson());
                return;
            }
            reporter = new PetNetworkPresenceReporter(
                    new HttpPresenceGateway(configured.orElseThrow()),
                    Clock.systemUTC(), UUID::randomUUID);
            Set<UUID> onlineOwners = proxy.getAllPlayers().stream()
                    .map(player -> player.getUniqueId())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            reporter.reconcileOnline(onlineOwners).whenComplete((value, failure) -> {
                if (failure != null) {
                    logger.warn(StructuredPetEvent.operation("presence_reconcile")
                            .count(onlineOwners.size()).failure(failure).outcome("failed").toJson());
                } else {
                    logger.info(StructuredPetEvent.operation("presence_reconcile")
                            .count(onlineOwners.size()).outcome("applied").toJson());
                }
            });
            logger.info(StructuredPetEvent.operation("velocity_presence_config")
                    .outcome("enabled").toJson());
        } catch (RuntimeException failure) {
            logger.error(StructuredPetEvent.operation("velocity_presence_config")
                    .failure(failure).outcome("invalid_disabled").toJson());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        publishOnline(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (reporter == null) return;
        UUID owner = event.getPlayer().getUniqueId();
        reporter.ownerDisconnected(owner).whenComplete((value, failure) -> {
            if (failure != null) logFailure("presence_offline", owner, failure);
        });
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (reporter != null) reporter.awaitDrain(5, TimeUnit.SECONDS);
    }

    private void publishOnline(UUID owner) {
        if (reporter == null) return;
        reporter.ownerConnected(owner).whenComplete((value, failure) -> {
            if (failure != null) logFailure("presence_online", owner, failure);
        });
    }

    private void logFailure(String operation, UUID owner, Throwable failure) {
        logger.warn(StructuredPetEvent.operation(operation).owner(owner)
                .failure(failure).outcome("failed").toJson());
    }
}
