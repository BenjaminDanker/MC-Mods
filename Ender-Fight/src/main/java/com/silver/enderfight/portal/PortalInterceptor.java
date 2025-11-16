package com.silver.enderfight.portal;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
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
 * Overworld. Integrate with the MCServerPortals bridge so the redirect can piggyback on the existing
 * transfer packet code.
 */
public final class PortalInterceptor {
    private static ConfigManager configManager;
    private static final long REDIRECT_COOLDOWN_MS = 3_000L;
    private static final String END_EXIT_TAG = "ender_exit";
    private static final Map<UUID, Long> recentRedirects = new ConcurrentHashMap<>();
    private static final Set<UUID> suppressedRedirects = ConcurrentHashMap.newKeySet();
    private static volatile boolean trackingLookupAttempted;
    private static Class<?> portalTrackingClass;
    private static Method trackingSetLastMethod;

    private PortalInterceptor() {
    }

    public static void register(ConfigManager configManager) {
        PortalInterceptor.configManager = configManager;
        EndControlConfig config = configManager.getConfig();
        
        EnderFightMod.LOGGER.info("PortalInterceptor.register() called");
        EnderFightMod.LOGGER.info("Portal redirect enabled: {}", config.portalRedirectEnabled());
        EnderFightMod.LOGGER.info("Portal redirect command: {}", config.portalRedirectCommand());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EndControlConfig startConfig = configManager.getConfig();
            if (!startConfig.portalRedirectEnabled()) {
                EnderFightMod.LOGGER.warn("Portal interception DISABLED on server start");
                return;
            }

            installPortalHook(server, startConfig);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> recentRedirects.clear());

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            EnderFightMod.LOGGER.info("AFTER_PLAYER_CHANGE_WORLD event fired: {} {} -> {} (player now at {}, {}, {})", 
                player.getName().getString(), 
                origin.getRegistryKey().getValue(), 
                destination.getRegistryKey().getValue(),
                player.getX(), player.getY(), player.getZ());
            
            if (PortalInterceptor.configManager == null) {
                EnderFightMod.LOGGER.warn("Config manager is null during world change event");
                return;
            }

            EndControlConfig eventConfig = PortalInterceptor.configManager.getConfig();
            if (!eventConfig.portalRedirectEnabled()) {
                EnderFightMod.LOGGER.debug("Portal redirect disabled, skipping interception");
                return;
            }

            EnderFightMod.LOGGER.info("Processing portal teleport for {}", player.getName().getString());
            handlePortalTeleport(player, origin.getRegistryKey(), destination.getRegistryKey(), eventConfig);
        });
    }

    private static void installPortalHook(MinecraftServer server, EndControlConfig config) {
        String command = normalizeCommand(config.portalRedirectCommand());
        if (command == null) {
            EnderFightMod.LOGGER.warn("Portal interception enabled but no redirect command or target server configured");
            return;
        }

        EnderFightMod.LOGGER.info("Portal interception active; End exit portals will execute '{}'", command);
    }

    /**
     * Attempt to intercept an End portal teleport. Returns true if the teleport was intercepted and handled.
     */
    public static boolean tryInterceptEndPortal(ServerPlayerEntity player) {
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
        
        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastRedirect = recentRedirects.get(player.getUuid());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            EnderFightMod.LOGGER.info("Player {} is on redirect cooldown, allowing normal End exit", player.getName().getString());
            return false;
        }
        
        // Attempt redirect
        if (redirectPlayer(player, config)) {
            recentRedirects.put(player.getUuid(), now);
            markPortalExit(player);
            EnderFightMod.LOGGER.info("Successfully intercepted End portal for player {}", player.getName().getString());
            return true;
        }
        
        return false;
    }

    /**
     * Example stub that decides whether the teleport should be intercepted.
     */
    protected static boolean shouldRedirect(RegistryKey<World> origin, RegistryKey<World> destination) {
        boolean originManagedEnd = isManagedEndDimension(origin);
        boolean destinationOverworld = World.OVERWORLD.equals(destination);
        boolean originCustomEnd = isCustomEndDimension(origin);
        boolean destinationVanillaEnd = World.END.equals(destination);
        boolean originVanillaEnd = World.END.equals(origin);

        boolean exitToOverworld = originManagedEnd && destinationOverworld;
        boolean exitCustomToVanilla = originCustomEnd && destinationVanillaEnd;
        boolean vanillaExitToOverworld = originVanillaEnd && destinationOverworld;

        boolean shouldRedirect = exitToOverworld || exitCustomToVanilla || vanillaExitToOverworld;

        EnderFightMod.LOGGER.info(
            "shouldRedirect check: {} -> {} = {} (originManagedEnd={}, destinationOverworld={}, originCustomEnd={}, destinationVanillaEnd={}, vanillaExit={})",
            origin.getValue(),
            destination.getValue(),
            shouldRedirect,
            originManagedEnd,
            destinationOverworld,
            originCustomEnd,
            destinationVanillaEnd,
            vanillaExitToOverworld
        );

        return shouldRedirect;
    }

    /**
     * Sends the player to the configured upstream server by executing the portal redirect command.
     * Mirrors the exact approach used by MCServerPortals.
     */
    protected static boolean redirectPlayer(Entity entity, EndControlConfig config) {
        String command = normalizeCommand(config.portalRedirectCommand());
        if (command == null) {
            EnderFightMod.LOGGER.warn("Portal redirect command missing; skipping redirect for {}", entity.getName().getString());
            return false;
        }

        EnderFightMod.LOGGER.info("Redirect triggered for {} using command '{}'", entity.getName().getString(), command);

        // Check if entity is a ServerPlayerEntity
        if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) {
            EnderFightMod.LOGGER.warn("Entity is not a ServerPlayerEntity, cannot execute command");
            return false;
        }

        var server = entity.getEntityWorld().getServer();
        if (server == null) {
            EnderFightMod.LOGGER.warn("Unable to redirect {}; server handle missing", entity.getName().getString());
            return false;
        }

        String displayName = extractServerName(command);
        if (displayName == null) {
            displayName = "next server";
        }
        player.sendMessage(Text.literal("Redirecting you to " + displayName + "..."), false);
        EnderFightMod.LOGGER.info("Queued command execution on main thread for {}", entity.getName().getString());

        // Execute the command on the server's main thread to ensure proper context
        server.execute(() -> {
            try {
                String commandWithSlash = command.startsWith("/") ? command : "/" + command;
                EnderFightMod.LOGGER.info("Executing command on main thread: {}", commandWithSlash);
                server.getCommandManager().executeWithPrefix(player.getCommandSource(), commandWithSlash);
                EnderFightMod.LOGGER.info("Command execution completed for portal redirect");
            } catch (Exception e) {
                EnderFightMod.LOGGER.error("Error executing portal command: {}", command, e);
            }
        });

        return true;
    }
    
    /**
     * Extracts the target server name from the configured command.
     * Expected formats: "wl portal SERVER" or "wl portal SERVER TOKEN"
     */
    private static String extractServerName(String command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        
        String[] parts = normalized.split("\\s+");
        
        // Format: "wl portal <server> [token]"
        if (parts.length >= 3 && "wl".equals(parts[0]) && "portal".equals(parts[1])) {
            return parts[2];
        }
        
        // Fallback: if format is just "<server>", use it directly
        if (parts.length == 1) {
            return parts[0];
        }
        
        return null;
    }
    
    /**
     * Helper that can be called from mixins to unify interception and logging.
     */
    public static void handlePortalTeleport(ServerPlayerEntity player, RegistryKey<World> from, RegistryKey<World> to, EndControlConfig config) {
        EnderFightMod.LOGGER.debug("Teleport event: {} moving from {} to {}", player.getName().getString(), from.getValue(), to.getValue());

        boolean redirectEligible = shouldRedirect(from, to);
        if (!redirectEligible) {
            EnderFightMod.LOGGER.debug("Teleport not eligible for redirect ({} -> {})", from.getValue(), to.getValue());
            return;
        }
        
        // Check if this is a MCServerPortals handoff - if so, skip interception
        try {
            Class<?> serverPortalsModClass = Class.forName("de.michiruf.serverportals.ServerPortalsMod");
            UUID playerId = player.getUuid();

            boolean skipForPending = invokeServerPortalsBoolean(serverPortalsModClass, "hasPendingPortalTeleport", playerId);
            boolean skipForQueued = invokeServerPortalsBoolean(serverPortalsModClass, "hasQueuedLoginHandoff", playerId);
            EnderFightMod.LOGGER.debug("PortalInterceptor handoff status for {}: pending={} queued={}",
                    player.getName().getString(),
                    skipForPending,
                    skipForQueued);
            if (skipForPending || skipForQueued) {
                EnderFightMod.LOGGER.info("Skipping portal redirect for {} - MCServerPortals handoff pending={}, queued={}"
                        , player.getName().getString()
                        , skipForPending
                        , skipForQueued);
                return;
            }
        } catch (Exception e) {
            EnderFightMod.LOGGER.debug("Could not check MCServerPortals pending/queued state: {}", e.getMessage());
        }

        if (consumeRedirectSuppression(player)) {
            EnderFightMod.LOGGER.debug("Skipping portal redirect for {} due to suppression", player.getName().getString());
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRedirect = recentRedirects.get(player.getUuid());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            EnderFightMod.LOGGER.debug("Skipping duplicate redirect for {} within cooldown", player.getName().getString());
            return;
        }

        if (redirectPlayer(player, config)) {
            recentRedirects.put(player.getUuid(), now);
            markPortalExit(player);
        }
    }

    public static void suppressNextRedirect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        suppressedRedirects.add(player.getUuid());
    }

    private static String normalizeCommand(String configuredCommand) {
        if (configuredCommand == null) {
            return null;
        }
        String command = configuredCommand.trim();
        if (command.isEmpty()) {
            return null;
        }
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        return command;
    }

    private static void markPortalExit(ServerPlayerEntity player) {
        ensurePortalTrackingReflection();
        if (portalTrackingClass == null || trackingSetLastMethod == null) {
            return;
        }

        if (!portalTrackingClass.isInstance(player)) {
            return;
        }

        try {
            trackingSetLastMethod.invoke(player, END_EXIT_TAG);
        } catch (ReflectiveOperationException ex) {
            EnderFightMod.LOGGER.debug("Failed to update ServerPortals tracking for {}", player.getName().getString(), ex);
        }
    }

    private static void ensurePortalTrackingReflection() {
        if (trackingLookupAttempted) {
            return;
        }

        synchronized (PortalInterceptor.class) {
            if (trackingLookupAttempted) {
                return;
            }

            try {
                portalTrackingClass = Class.forName("de.michiruf.serverportals.api.EntityPortalTracking");
                trackingSetLastMethod = portalTrackingClass.getMethod("serverportals$setLastPortalName", String.class);
                EnderFightMod.LOGGER.info("Detected MCServerPortals tracking API; portal tags will be propagated");
            } catch (ClassNotFoundException | NoSuchMethodException ex) {
                EnderFightMod.LOGGER.debug("MCServerPortals tracking API not available; skipping portal tag integration");
                portalTrackingClass = null;
                trackingSetLastMethod = null;
            } finally {
                trackingLookupAttempted = true;
            }
        }
    }

    public static boolean isManagedEndDimension(RegistryKey<World> key) {
        if (key == null) {
            return false;
        }
        return World.END.equals(key) || isCustomEndDimension(key);
    }

    private static boolean isCustomEndDimension(RegistryKey<World> key) {
        if (key == null) {
            return false;
        }
        Identifier id = key.getValue();
        return EnderFightMod.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("daily_end_");
    }

    private static boolean consumeRedirectSuppression(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        return suppressedRedirects.remove(player.getUuid());
    }

    private static boolean invokeServerPortalsBoolean(Class<?> modClass, String methodName, UUID playerId) {
        try {
            java.lang.reflect.Method method = modClass.getMethod(methodName, UUID.class);
            Object result = method.invoke(null, playerId);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
