package com.silver.enderfight.portal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Observes End portal teleports and forwards the players to a designated vanilla server instead of the
 * Overworld. Integrate with Portals so the redirect can piggyback on the existing
 * transfer packet code.
 */
public final class PortalInterceptor {
    private static final String SKY_ISLANDS_ENTRANCE_PORTAL = "sky-island_entrance";
    private static ConfigManager configManager;
    private static final long REDIRECT_COOLDOWN_MS = 3_000L;
    private static final long EXIT_REQUIRED_OUTSIDE_MS = 1_000L;
    private static final String END_EXIT_TAG = "";
    private static final Map<UUID, Long> recentRedirects = new ConcurrentHashMap<>();
    private static final Set<UUID> suppressedRedirects = ConcurrentHashMap.newKeySet();

    // End portal collision gating: require a clean re-entry after being outside for a moment.
    private static final Map<UUID, Boolean> lastEndPortalPresence = new ConcurrentHashMap<>();
    private static final Set<UUID> endPortalPresenceInitialized = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> endPortalExitRequired = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> endPortalOutsideSince = new ConcurrentHashMap<>();


    private PortalInterceptor() {
    }

    public static void register(ConfigManager configManager) {
        PortalInterceptor.configManager = configManager;
        EndControlConfig config = configManager.getConfig();
        
        EnderFightMod.LOGGER.info("PortalInterceptor.register() called");
        EnderFightMod.LOGGER.info("Portal redirect enabled: {}", config.portalRedirectEnabled());
        EnderFightMod.LOGGER.info("Portal redirect target server: {}", config.portalRedirectTargetServer());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EndControlConfig startConfig = configManager.getConfig();
            if (!startConfig.portalRedirectEnabled()) {
                EnderFightMod.LOGGER.warn("Portal interception DISABLED on server start");
                return;
            }

            installPortalHook(server, startConfig);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> recentRedirects.clear());

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            lastEndPortalPresence.clear();
            endPortalPresenceInitialized.clear();
            endPortalExitRequired.clear();
            endPortalOutsideSince.clear();
            suppressedRedirects.clear();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearEndPortalTracking(handler.getPlayer()));

    }

    private static void clearEndPortalTracking(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        lastEndPortalPresence.remove(playerId);
        endPortalPresenceInitialized.remove(playerId);
        endPortalExitRequired.remove(playerId);
        endPortalOutsideSince.remove(playerId);
        recentRedirects.remove(playerId);
        suppressedRedirects.remove(playerId);
    }

    /**
     * Tracks whether a player is currently in an End portal block and returns true only when this tick
     * represents a clean outside -> inside transition that should be eligible for redirect.
     *
     * Behaviour:
     * - If the first observed state is already "inside portal", mark the player as requiring an exit.
     * - While exit is required, the player must remain outside the portal for 1 second before redirects
     *   can trigger on the next entry.
     */
    public static boolean onEndPortalPresenceTick(ServerPlayer player, boolean inPortalBlock) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();

        boolean initialized = endPortalPresenceInitialized.contains(playerId);
        boolean wasInPortal = Boolean.TRUE.equals(lastEndPortalPresence.get(playerId));

        if (!initialized) {
            endPortalPresenceInitialized.add(playerId);
            lastEndPortalPresence.put(playerId, inPortalBlock);
            if (inPortalBlock) {
                endPortalExitRequired.add(playerId);
                endPortalOutsideSince.remove(playerId);
                EnderFightMod.LOGGER.info(
                    "Player {} detected inside End portal on first tick; requiring exit for {}ms before redirect",
                    player.getName().getString(),
                    EXIT_REQUIRED_OUTSIDE_MS
                );
            }
            return false;
        }

        if (inPortalBlock) {
            lastEndPortalPresence.put(playerId, true);
            endPortalOutsideSince.remove(playerId);
            if (!wasInPortal) {
                // Enter edge
                return !endPortalExitRequired.contains(playerId);
            }
            return false;
        }

        // Outside portal
        lastEndPortalPresence.put(playerId, false);

        if (endPortalExitRequired.contains(playerId)) {
            Long since = endPortalOutsideSince.get(playerId);
            if (since == null) {
                endPortalOutsideSince.put(playerId, now);
            } else if (now - since >= EXIT_REQUIRED_OUTSIDE_MS) {
                endPortalExitRequired.remove(playerId);
                endPortalOutsideSince.remove(playerId);
                EnderFightMod.LOGGER.info(
                    "Player {} exited End portal long enough; redirect eligible on next entry",
                    player.getName().getString()
                );
            }
        } else {
            endPortalOutsideSince.remove(playerId);
        }

        return false;
    }

    private static void installPortalHook(MinecraftServer server, EndControlConfig config) {
        String targetServer = config.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            EnderFightMod.LOGGER.warn("Portal interception enabled but no redirect target server configured");
            return;
        }

        EnderFightMod.LOGGER.info("Portal interception active; End exit portals will request proxy transfer to '{}'", targetServer);
    }

    /**
     * Attempt to intercept an End portal teleport. Returns true if the teleport was intercepted and handled.
     */
    public static boolean tryInterceptEndPortal(ServerPlayer player) {
        if (configManager == null) {
            EnderFightMod.LOGGER.warn("Config manager not initialized during End portal intercept");
            return false;
        }
        
        EndControlConfig config = configManager.getConfig();
        if (!config.portalRedirectEnabled()) {
            EnderFightMod.LOGGER.debug("Portal redirect disabled, allowing normal End exit");
            return false;
        }

        if (consumeRedirectSuppression(player)) {
            EnderFightMod.LOGGER.debug("Skipping End portal collision redirect for {} due to suppression",
                    player.getName().getString());
            return false;
        }

        // A player arriving through a portal (e.g. logging in while standing in one) is left alone, or they bounce back.
        if (com.silver.portals.Portals.arrivalPending(player.getUUID())) {
            EnderFightMod.LOGGER.debug("Skipping End portal collision redirect for {}: arriving through a portal", player.getName().getString());
            return false;
        }
        
        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastRedirect = recentRedirects.get(player.getUUID());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            EnderFightMod.LOGGER.info("Player {} is on redirect cooldown, allowing normal End exit", player.getName().getString());
            return false;
        }
        
        // Attempt redirect
        if (redirectPlayer(player, config)) {
            recentRedirects.put(player.getUUID(), now);
            markPortalExit(player);
            EnderFightMod.LOGGER.info("Successfully intercepted End portal for player {}", player.getName().getString());
            return true;
        }
        
        return false;
    }

    /**
     * Example stub that decides whether the teleport should be intercepted.
     */
    protected static boolean shouldRedirect(ResourceKey<Level> origin, ResourceKey<Level> destination) {
        boolean originManagedEnd = isManagedEndDimension(origin);
        boolean destinationOverworld = Level.OVERWORLD.equals(destination);
        boolean shouldRedirect = originManagedEnd && destinationOverworld;

        EnderFightMod.LOGGER.info(
            "shouldRedirect check: {} -> {} = {} (originManagedEnd={}, destinationOverworld={})",
            origin.identifier(),
            destination.identifier(),
            shouldRedirect,
            originManagedEnd,
            destinationOverworld
        );

        return shouldRedirect;
    }

    public static boolean isEndPortalExitRequired(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return endPortalExitRequired.contains(player.getUUID());
    }

    public static boolean isPortalRedirectEnabled() {
        if (configManager == null) {
            return false;
        }
        EndControlConfig cfg = configManager.getConfig();
        if (cfg == null || !cfg.portalRedirectEnabled()) {
            return false;
        }
        String targetServer = cfg.portalRedirectTargetServer();
        return targetServer != null && !targetServer.isBlank();
    }

    /**
     * Sends the player to the configured upstream server by requesting a signed transfer via Portals.
     */
    protected static boolean redirectPlayer(Entity entity, EndControlConfig config) {
        String targetServer = config.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            EnderFightMod.LOGGER.warn("Portal redirect target server missing; skipping redirect for {}", entity.getName().getString());
            return false;
        }


        String destinationPortal = resolvePortalHandoffName(config, targetServer.trim());

        EnderFightMod.LOGGER.info("Redirect triggered for {} -> {} via signed portal request", entity.getName().getString(), targetServer);

        // Check if entity is a ServerPlayerEntity
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) {
            EnderFightMod.LOGGER.warn("Entity is not a ServerPlayerEntity, cannot execute command");
            return false;
        }

        player.sendSystemMessage(Component.literal("Redirecting you to " + targetServer + "..."), false);

        try {
            if (!com.silver.portals.Portals.transfer(player, targetServer, destinationPortal, "end portal redirect")) return false;
            EnderFightMod.LOGGER.info("Queued Ender-Fight redirect for {} -> {} after pet preparation", player.getName().getString(), targetServer);
        } catch (Exception ex) {
            EnderFightMod.LOGGER.error("Failed sending Ender-Fight portal request for {}", player.getName().getString(), ex);
            return false;
        }

        return true;
    }

    private static String resolvePortalHandoffName(EndControlConfig config, String targetServer) {
        return SKY_ISLANDS_ENTRANCE_PORTAL;
    }
    
    /**
     * Helper that can be called from mixins to unify interception and logging.
     */
    public static void handlePortalTeleport(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to, EndControlConfig config) {
        EnderFightMod.LOGGER.debug("Teleport event: {} moving from {} to {}", player.getName().getString(), from.identifier(), to.identifier());

        boolean redirectEligible = shouldRedirect(from, to);
        if (!redirectEligible) {
            EnderFightMod.LOGGER.debug("Teleport not eligible for redirect ({} -> {})", from.identifier(), to.identifier());
            return;
        }
        
        // A player arriving through a portal is left alone.
        if (com.silver.portals.Portals.arrivalPending(player.getUUID())) {
            EnderFightMod.LOGGER.info("Skipping portal redirect for {}: arriving through a portal", player.getName().getString());
            return;
        }

        if (consumeRedirectSuppression(player)) {
            EnderFightMod.LOGGER.debug("Skipping portal redirect for {} due to suppression", player.getName().getString());
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRedirect = recentRedirects.get(player.getUUID());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            EnderFightMod.LOGGER.debug("Skipping duplicate redirect for {} within cooldown", player.getName().getString());
            return;
        }

        if (redirectPlayer(player, config)) {
            recentRedirects.put(player.getUUID(), now);
            markPortalExit(player);
        }
    }

    public static void suppressNextRedirect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        suppressedRedirects.add(player.getUUID());
    }

    /** Tells Portals the player left through the End exit, so the portal there won't send them straight back. */
    private static void markPortalExit(ServerPlayer player) {
        com.silver.portals.Portals.cameThrough(player, END_EXIT_TAG);
    }

    public static boolean isManagedEndDimension(ResourceKey<Level> key) {
        if (key == null) {
            return false;
        }
        return Level.END.equals(key) || isCustomEndDimension(key);
    }

    private static boolean isCustomEndDimension(ResourceKey<Level> key) {
        if (key == null) {
            return false;
        }
        Identifier id = key.identifier();
        return EnderFightMod.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("daily_end_");
    }

    private static boolean consumeRedirectSuppression(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return suppressedRedirects.remove(player.getUUID());
    }

}
