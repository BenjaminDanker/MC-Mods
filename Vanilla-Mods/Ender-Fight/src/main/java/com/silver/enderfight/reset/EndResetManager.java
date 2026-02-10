package com.silver.enderfight.reset;

import com.google.common.collect.ImmutableList;
import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
import com.silver.enderfight.dragon.DragonBreathModifier;
import com.silver.enderfight.duck.NoiseChunkGeneratorExtension;
import com.silver.enderfight.duck.ServerWorldDuck;
import com.silver.enderfight.mixin.MinecraftServerAccessor;
import com.silver.enderfight.mixin.SimpleRegistryAccessor;
import com.silver.enderfight.portal.PortalInterceptor;
import com.silver.enderfight.util.WorldSeedOverrides;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.TheEndBiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import net.minecraft.world.spawner.SpecialSpawner;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Coordinates the End reset lifecycle. A tick-based state machine keeps the behaviour deterministic and
 * reusable no matter how often the server restarts.
 */
public class EndResetManager {
    private static final int WALL_CLOCK_CHECK_INTERVAL_TICKS = 20;
    private static final DateTimeFormatter DIMENSION_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String DRAGON_BOSS_BAR_FIELD_NAME = "field_13119"; // obfuscated getter for EnderDragonFight#bossBar

    private static final Identifier STABLE_END_ID = Identifier.of(EnderFightMod.MOD_ID, "daily_end_active");
    private static final RegistryKey<World> STABLE_END_WORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, STABLE_END_ID);

    private final ConfigManager configManager;

    private EndControlConfig cachedConfig;
    private EndResetPersistentState persistentState;
    private boolean countdownActive;
    private long countdownTicksRemaining;
    private boolean warningSent;
    private RegistryKey<World> activeEndWorldKey = World.END;
    private int wallClockCheckAccumulator;
    private boolean resetIntervalElapsed;
    private boolean warningWindowExceeded;

    private static final int VANILLA_END_REDIRECT_DELAY_TICKS = 1;
    private final Map<UUID, Integer> pendingVanillaEndRedirects = new ConcurrentHashMap<>();

    private static final BlockPos END_PLATFORM_BASE = new BlockPos(100, 49, 0);
    private static final Vec3d END_PLATFORM_SPAWN = Vec3d.ofCenter(END_PLATFORM_BASE).add(0.0, 1.0, 0.0);
    private static final float END_PLATFORM_YAW = 180.0F;
    private static java.lang.reflect.Field dragonBossBarField;

    public EndResetManager(ConfigManager configManager) {
        this.configManager = configManager;
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(this::handlePlayerWorldChange);
        ServerPlayConnectionEvents.JOIN.register(this::handlePlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::handlePlayerDisconnect);
    }

    public void onServerStarting(MinecraftServer server) {
        if (server == null) {
            return;
        }

        // Important: SERVER_STARTING fires before worlds are fully created.
        // This is the safest place to remove stale dimensions so Minecraft won't recreate their
        // empty directories or attempt to save them later.
        EndResetPersistentState state = EndResetPersistentState.load(server);

        // If the previous implementation used timestamped daily_end_* ids, migrate the active one to a
        // single stable id so we can truly retire old dimensions instead of accumulating persistent defs.
        migrateActiveEndToStableDirectory(server, state);

        RegistryKey<World> activeKeyToKeep = state.getActiveDimensionKey();

        // If any prior build produced duplicate entries in the DIMENSION registry's raw-id tables,
        // Minecraft can crash on shutdown while serializing registries. Rebuild the tables up front.
        MutableRegistry<DimensionOptions> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry != null) {
            rebuildDimensionRegistryIndices(dimensionRegistry);
        }

        unregisterStaleEndDimensions(server, activeKeyToKeep);
        cleanupStaleEndDimensions(server, activeKeyToKeep);
    }

    public void onServerStarted(MinecraftServer server) {
        this.persistentState = EndResetPersistentState.load(server);
        this.cachedConfig = configManager.getConfig();
        this.activeEndWorldKey = persistentState.getActiveDimensionKey();
        // NOTE: Do not delete dimension folders on startup.
        // By the time SERVER_STARTED fires, Minecraft has already created ServerWorld instances
        // for every registered dimension; deleting their folders here causes save failures on stop.
        ensureActiveEndWorld(server);
    }

    public void onServerStopping(MinecraftServer server) {
        if (server == null) {
            return;
        }

        RegistryKey<World> activeKeyToKeep = activeEndWorldKey;
        if (persistentState != null) {
            activeKeyToKeep = persistentState.getActiveDimensionKey();
            persistentState.save(server);
        }

        // Remove old dimension definitions and loaded worlds so Minecraft does not keep recreating
        // empty daily_end_* folders and trying to save them.
        unregisterStaleEndDimensions(server, activeKeyToKeep);
    }

    public void onServerStopped(MinecraftServer server) {
        RegistryKey<World> activeKeyToKeep = activeEndWorldKey;
        if (persistentState != null) {
            activeKeyToKeep = persistentState.getActiveDimensionKey();
            // State is already persisted during SERVER_STOPPING; keep this as a last-resort fallback.
            persistentState.save(server);
        }

        // Filesystem cleanup is safe after worlds have been closed.
        cleanupStaleEndDimensions(server, activeKeyToKeep);
        WorldSeedOverrides.clear();
        this.persistentState = null;
        this.countdownActive = false;
        this.warningSent = false;
        this.activeEndWorldKey = World.END;
    }

    public void tick(MinecraftServer server) {
        if (persistentState == null) {
            return;
        }

        cachedConfig = configManager.getConfig();

        processPendingVanillaEndRedirects(server);

        updateResetScheduleFlags();

        if (!countdownActive) {
            if (resetIntervalElapsed) {
                beginCountdown();
            }
            return;
        }

        if (warningWindowExceeded && countdownTicksRemaining > 0) {
            EnderFightMod.LOGGER.info("Reset overdue while server was idle; fast-forwarding countdown ({} ticks remaining)", countdownTicksRemaining);
            countdownTicksRemaining = 0;
        }

        processCountdown(server);
    }

    private void beginCountdown() {
        this.countdownActive = true;
        this.warningSent = false;
        long warningTicks = cachedConfig.warningDelayTicks();
        this.countdownTicksRemaining = warningTicks;
        double intervalHours = cachedConfig.resetIntervalHours();
        int warningSeconds = cachedConfig.resetWarningSeconds();
        EnderFightMod.LOGGER.info(
            "End reset countdown started (interval={}h); warning window {} ticks ({}s)",
            intervalHours,
            warningTicks,
            warningSeconds
        );
    }

    private void processCountdown(MinecraftServer server) {
        if (!warningSent) {
            notifyPlayersOfReset(server);
            warningSent = true;
        }

        if (countdownTicksRemaining > 0) {
            countdownTicksRemaining--;
            return;
        }

        boolean success = performReset(server);
        if (success) {
            countdownActive = false;
            warningSent = false;
            onResetCompleted();
        } else {
            EnderFightMod.LOGGER.warn("Scheduled End reset failed; countdown cancelled (check logs)");
            countdownActive = false;
            warningSent = false;
        }
    }

    private void notifyPlayersOfReset(MinecraftServer server) {
        ServerWorld endWorld = getActiveEndWorld(server);
        if (endWorld == null) {
            return;
        }

        Text message = Text.literal(cachedConfig.warningMessage());
        for (ServerPlayerEntity player : endWorld.getPlayers()) {
            player.sendMessage(message, false);
        }
        EnderFightMod.LOGGER.info("Warned {} players about upcoming End reset", endWorld.getPlayers().size());
    }

    private boolean performReset(MinecraftServer server) {
        if (server == null) {
            EnderFightMod.LOGGER.warn("Cannot reset End – server reference was null");
            return false;
        }
        ServerWorld endWorld = getActiveEndWorld(server);
        if (endWorld == null) {
            EnderFightMod.LOGGER.warn("Could not acquire End world to reset; aborting");
            return false;
        }

        List<ServerPlayerEntity> playersInEnd = new ArrayList<>(endWorld.getPlayers());
        if (playersInEnd.isEmpty()) {
            EnderFightMod.LOGGER.info("Resetting {} – no players detected in End dimension {}; skipping teleport", endWorld.getRegistryKey().getValue(), endWorld.getRegistryKey());
        } else {
            String names = playersInEnd.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
            EnderFightMod.LOGGER.info("Resetting {} – teleporting players out: {}", endWorld.getRegistryKey().getValue(), names);
            teleportPlayersToOverworld(server, playersInEnd);
        }

        long newSeed = ThreadLocalRandom.current().nextLong();
        boolean rebuilt = rebuildEndWorld(server, endWorld, endWorld.getRegistryKey(), newSeed);
        if (!rebuilt) {
            EnderFightMod.LOGGER.warn("End reset aborted after failing to rebuild the dimension");
            return false;
        }

        ensureDragonFightState(server);

        persistentState.updateOnReset(Instant.now().toEpochMilli(), newSeed, activeEndWorldKey);
        persistentState.save(server);
        EnderFightMod.LOGGER.info("End reset completed; new seed {} recorded", newSeed);
        return true;
    }

    private void ensureDragonFightState(MinecraftServer server) {
        ServerWorld endWorld = getActiveEndWorld(server);
        if (endWorld == null) {
            return;
        }
        EnderDragonFight fight = endWorld.getEnderDragonFight();
        if (fight == null) {
            return;
        }

        fight.respawnDragon();
        EnderFightMod.LOGGER.info("Forced EnderDragonFight respawn to regenerate exit portal and gateway state");
    }

    public boolean triggerManualReset(MinecraftServer server) {
        if (persistentState == null) {
            EnderFightMod.LOGGER.warn("Manual reset requested before persistent state initialised; ignoring");
            return false;
        }
        cachedConfig = configManager.getConfig();
        boolean success = performReset(server);
        if (success) {
            countdownActive = false;
            warningSent = false;
            onResetCompleted();
        }
        return success;
    }

    private void updateResetScheduleFlags() {
        wallClockCheckAccumulator++;
        if (wallClockCheckAccumulator < WALL_CLOCK_CHECK_INTERVAL_TICKS) {
            return;
        }
        wallClockCheckAccumulator = 0;

        if (cachedConfig == null) {
            cachedConfig = configManager.getConfig();
        }

        long lastReset = persistentState.getLastResetEpochMillis();
        double intervalHours = cachedConfig.resetIntervalHours();
        long intervalMillis = (long) Math.max(1D, intervalHours * 3_600_000D);
        long warningTicks = Math.max(0L, (long) cachedConfig.warningDelayTicks());
        long warningMillis = warningTicks * 50L;
        long now = Instant.now().toEpochMilli();

        if (lastReset <= 0L) {
            resetIntervalElapsed = true;
            warningWindowExceeded = false;
            return;
        }

        long elapsed = now - lastReset;
        resetIntervalElapsed = elapsed >= intervalMillis;
        warningWindowExceeded = elapsed >= intervalMillis + warningMillis;
    }

    private void onResetCompleted() {
        resetIntervalElapsed = false;
        warningWindowExceeded = false;
        wallClockCheckAccumulator = 0;
    }

    /**
     * Collects all players currently residing in the End dimension. Exposed for unit tests and mixin hooks.
     */
    protected List<ServerPlayerEntity> detectPlayersInEnd(ServerWorld endWorld) {
        return Collections.unmodifiableList(new ArrayList<>(endWorld.getPlayers()));
    }

    /**
     * Teleports the supplied players back to the overworld spawn using FabricDimensions.
     */
    protected void teleportPlayersToOverworld(MinecraftServer server, List<ServerPlayerEntity> players) {
        Text notification = Text.literal(cachedConfig.teleportMessage());
        teleportPlayersToOverworld(server, players, notification, "End reset");
    }

    public void teleportPlayerToOverworld(ServerPlayerEntity player, Text message, String logContext) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getCommandSource().getServer();
        if (server == null) {
            return;
        }

        teleportPlayersToOverworld(server, java.util.List.of(player), message, logContext);
    }

    private void teleportPlayersToOverworld(MinecraftServer server, List<ServerPlayerEntity> players, Text message, String logContext) {
        if (players.isEmpty()) {
            return;
        }

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            EnderFightMod.LOGGER.error("Overworld missing while attempting to teleport End players");
            return;
        }

        MinecraftServer ownerServer = overworld.getServer();
        WorldProperties.SpawnPoint spawnPoint = ownerServer != null ? ownerServer.getSpawnPoint() : null;
        if (spawnPoint == null) {
            spawnPoint = overworld.getLevelProperties().getSpawnPoint();
        }

    BlockPos spawnPos = spawnPoint != null ? spawnPoint.getPos() : BlockPos.ORIGIN;
    Vec3d spawnVec = Vec3d.ofCenter(spawnPos);
        float spawnYaw = 0.0F;
        float spawnPitch = 0.0F;
        if (spawnPoint != null) {
            // Reflectively read yaw/pitch so we preserve configured spawn orientation even when mappings lack named helpers.
            try {
                spawnYaw = (float) WorldProperties.SpawnPoint.class.getMethod("yaw").invoke(spawnPoint);
                spawnPitch = (float) WorldProperties.SpawnPoint.class.getMethod("pitch").invoke(spawnPoint);
            } catch (ReflectiveOperationException ex) {
                EnderFightMod.LOGGER.debug("Unable to read spawn yaw/pitch from SpawnPoint record", ex);
            }
        }

        if (spawnPoint != null && !World.OVERWORLD.equals(spawnPoint.getDimension())) {
            EnderFightMod.LOGGER.warn("Server spawn point dimension {} differs from overworld; using position {} regardless", spawnPoint.getDimension().getValue(), spawnPos);
        }

        for (ServerPlayerEntity player : players) {
            if (ownerServer != null) {
                ownerServer.getBossBarManager().onPlayerDisconnect(player);
                EnderFightMod.LOGGER.info("Cleared boss bars for {} via disconnect hook", player.getName().getString());
            }

            tryRemoveFromDragonBossBar(player);

            PortalInterceptor.suppressNextRedirect(player);
            TeleportTarget target = new TeleportTarget(overworld, spawnVec, Vec3d.ZERO, spawnYaw, spawnPitch, TeleportTarget.NO_OP);
            player.teleportTo(target);
            if (message != null) {
                player.sendMessage(message, false);
            }
        }

        String names = players.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
        EnderFightMod.LOGGER.info("{} teleported {} players out of the End to {} (yaw {}, pitch {}): {}",
            logContext,
            players.size(),
            spawnPos,
            spawnYaw,
            spawnPitch,
            names);
    }

    private void handlePlayerWorldChange(ServerPlayerEntity player, ServerWorld origin, ServerWorld destination) {
        if (activeEndWorldKey == null || activeEndWorldKey.equals(World.END)) {
            return;
        }
        if (!destination.getRegistryKey().equals(World.END)) {
            return;
        }
        if (PortalInterceptor.isManagedEndDimension(origin.getRegistryKey())) {
            return;
        }
        MinecraftServer server = destination.getServer();
        if (server == null) {
            return;
        }
        ServerWorld targetWorld = server.getWorld(activeEndWorldKey);
        if (targetWorld == null || targetWorld == destination) {
            return;
        }

        UUID playerId = player.getUuid();
        pendingVanillaEndRedirects.putIfAbsent(playerId, VANILLA_END_REDIRECT_DELAY_TICKS);
        EnderFightMod.LOGGER.info(
            "Queued player {} for vanilla End -> custom End redirect in {} tick(s) (target={})",
            player.getName().getString(),
            VANILLA_END_REDIRECT_DELAY_TICKS,
            activeEndWorldKey.getValue()
        );
    }

    private void processPendingVanillaEndRedirects(MinecraftServer server) {
        if (pendingVanillaEndRedirects.isEmpty() || server == null) {
            return;
        }

        var iterator = pendingVanillaEndRedirects.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID playerId = entry.getKey();
            int remaining = entry.getValue() == null ? 0 : entry.getValue();

            if (remaining > 0) {
                entry.setValue(remaining - 1);
                continue;
            }

            iterator.remove();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                continue;
            }

            ServerWorld currentWorld = player.getCommandSource().getWorld();
            if (currentWorld == null || !World.END.equals(currentWorld.getRegistryKey())) {
                continue;
            }

            if (activeEndWorldKey == null || activeEndWorldKey.equals(World.END)) {
                continue;
            }

            ServerWorld targetWorld = server.getWorld(activeEndWorldKey);
            if (targetWorld == null || targetWorld == currentWorld) {
                continue;
            }

            EnderFightMod.LOGGER.info(
                "Redirecting player {} from vanilla End into custom End {} (delayed)",
                player.getName().getString(),
                activeEndWorldKey.getValue()
            );

            ensureEndSpawnPlatform(targetWorld);
            TeleportTarget target = new TeleportTarget(targetWorld, END_PLATFORM_SPAWN, Vec3d.ZERO, END_PLATFORM_YAW, 0.0F, TeleportTarget.NO_OP);
            player.teleportTo(target);
        }
    }

    private void handlePlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;

        DragonBreathModifier.purgeExtraSpecialDragonBreath(player, "player join");

        if (persistentState == null) {
            return;
        }

        RegistryKey<World> recordedKey = persistentState.getRecordedEndDimension(player.getUuid());
        if (recordedKey == null) {
            EnderFightMod.LOGGER.debug("No offline End record for {}; skipping safeguard", player.getName().getString());
            return;
        }

        EnderFightMod.LOGGER.info("Offline End record found for {}: recordedKey={} activeKey={}",
            player.getName().getString(),
            recordedKey.getValue(),
            getActiveEndWorldKey().getValue());

    RegistryKey<World> activeKey = getActiveEndWorldKey();

        ServerWorld currentWorld = player.getCommandSource().getWorld();
        if (currentWorld != null && !PortalInterceptor.isManagedEndDimension(currentWorld.getRegistryKey())) {
            EnderFightMod.LOGGER.info("Player {} already placed in {} after End reset; forcing return to spawn",
                player.getName().getString(), currentWorld.getRegistryKey().getValue());
            DragonBreathModifier.purgeExtraSpecialDragonBreath(player, "offline End reset (post-login spawn correction)");
            Text message = Text.literal("The End reset while you were offline; you've been returned to spawn.");
            teleportPlayersToOverworld(server, ImmutableList.of(player), message, "Offline End reset safeguard (post-login)");
            if (persistentState.clearRecordedPlayer(player.getUuid())) {
                persistentState.save(server);
            }
            return;
        }

        if (!PortalInterceptor.isManagedEndDimension(recordedKey) || recordedKey.equals(activeKey)) {
            EnderFightMod.LOGGER.info("Clearing offline End record for {} because recordedKey managed={} equalsActive={}",
                player.getName().getString(),
                PortalInterceptor.isManagedEndDimension(recordedKey),
                recordedKey.equals(activeKey));
            if (persistentState.clearRecordedPlayer(player.getUuid())) {
                persistentState.save(server);
            }
            return;
        }

        if (hasPendingServerPortalsHandoff(player)) {
            EnderFightMod.LOGGER.info("Skipping offline End safeguard for {} – pending ServerPortals handoff detected",
                player.getName().getString());
            if (persistentState.clearRecordedPlayer(player.getUuid())) {
                persistentState.save(server);
            }
            return;
        }

        EnderFightMod.LOGGER.info("Executing offline End safeguard for {} (recorded {}, active {})",
            player.getName().getString(), recordedKey.getValue(), activeKey.getValue());
        Text message = Text.literal("The End reset while you were offline; you've been returned to spawn.");
        DragonBreathModifier.purgeExtraSpecialDragonBreath(player, "offline End reset teleport");
        teleportPlayersToOverworld(server, ImmutableList.of(player), message, "Offline End reset safeguard");

        if (persistentState.clearRecordedPlayer(player.getUuid())) {
            persistentState.save(server);
        }
    }

    private void handlePlayerDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        if (persistentState == null) {
            return;
        }

        ServerPlayerEntity player = handler.player;
        pendingVanillaEndRedirects.remove(player.getUuid());
        RegistryKey<World> worldKey = player.getCommandSource().getWorld().getRegistryKey();

        boolean managedEnd = PortalInterceptor.isManagedEndDimension(worldKey);
        EnderFightMod.LOGGER.info("Player {} disconnecting from world {} (managedEnd={})",
            player.getName().getString(), worldKey.getValue(), managedEnd);

        tryRemoveFromDragonBossBar(player);

        boolean changed;
        if (managedEnd) {
            persistentState.recordPlayerLoggedOutInEnd(player.getUuid(), worldKey);
            EnderFightMod.LOGGER.info("Recorded {} as offline-in-End for {}", player.getName().getString(), worldKey.getValue());
            changed = true;
        } else {
            changed = persistentState.clearRecordedPlayer(player.getUuid());
            if (changed) {
                EnderFightMod.LOGGER.info("Cleared offline End record for {} after disconnect outside End", player.getName().getString());
            }
        }

        if (changed) {
            persistentState.save(server);
        }
    }

    private void tryRemoveFromDragonBossBar(ServerPlayerEntity player) {
        ServerWorld world = player.getCommandSource().getWorld();
        if (world == null) {
            return;
        }
        EnderDragonFight fight = world.getEnderDragonFight();
        if (fight == null) {
            return;
        }

        ServerBossBar bossBar = resolveDragonBossBar(fight);
        if (bossBar == null) {
            return;
        }

        if (bossBar.getPlayers().contains(player)) {
            bossBar.removePlayer(player);
            EnderFightMod.LOGGER.info("Removed {} from Ender dragon boss bar via reflection", player.getName().getString());
        }
    }

    private ServerBossBar resolveDragonBossBar(EnderDragonFight fight) {
        if (fight == null) {
            return null;
        }

        try {
            if (dragonBossBarField == null) {
                dragonBossBarField = EnderDragonFight.class.getDeclaredField(DRAGON_BOSS_BAR_FIELD_NAME);
                dragonBossBarField.setAccessible(true);
            }
            Object value = dragonBossBarField.get(fight);
            if (value instanceof ServerBossBar bossBar) {
                return bossBar;
            }
        } catch (ReflectiveOperationException ex) {
            EnderFightMod.LOGGER.debug("Unable to access EnderDragonFight#bossBar via reflection", ex);
        }
        return null;
    }

    public RegistryKey<World> getActiveEndWorldKey() {
        return activeEndWorldKey == null ? World.END : activeEndWorldKey;
    }

    public ServerWorld getActiveEndWorld(MinecraftServer server) {
        return server.getWorld(getActiveEndWorldKey());
    }

    private void ensureActiveEndWorld(MinecraftServer server) {
        if (activeEndWorldKey == null) {
            activeEndWorldKey = World.END;
        }
        if (activeEndWorldKey.equals(World.END)) {
            return;
        }
        ServerWorld existingWorld = server.getWorld(activeEndWorldKey);
        if (existingWorld != null) {
            EnderFightMod.LOGGER.debug("Existing End world {} detected on startup; ensuring spawn platform and dragon fight", activeEndWorldKey.getValue());
            ensureEndSpawnPlatform(existingWorld);
            initializeEndDragonFight(existingWorld);
            return;
        }

        MutableRegistry<DimensionOptions> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry == null) {
            EnderFightMod.LOGGER.warn("Unable to locate dimension registry; falling back to vanilla End");
            activeEndWorldKey = World.END;
            return;
        }

        DimensionOptions template = dimensionRegistry.get(DimensionOptions.END);
        if (template == null) {
            EnderFightMod.LOGGER.warn("Missing template End dimension options; unable to restore custom End");
            activeEndWorldKey = World.END;
            return;
        }

        long seed = persistentState.getCurrentEndSeed();
        NoiseChunkGenerator generator = createEndChunkGenerator(server, seed);
        if (generator == null) {
            activeEndWorldKey = World.END;
            return;
        }

        DimensionOptions options = new DimensionOptions(template.dimensionTypeEntry(), generator);
        registerDimensionOptions(dimensionRegistry, activeEndWorldKey, options);

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        ServerWorld newWorld = instantiateEndWorld(server, accessor, activeEndWorldKey, options, seed);
        accessor.getWorlds().put(activeEndWorldKey, newWorld);
        initializeEndDragonFight(newWorld);
    }

    public BlockPos getEndSpawnPlatformBase() {
        return END_PLATFORM_BASE;
    }

    public Vec3d getEndSpawnLocation() {
        return END_PLATFORM_SPAWN;
    }

    public float getEndSpawnYaw() {
        return END_PLATFORM_YAW;
    }

    public void ensureEndSpawnPlatform(ServerWorld world) {
        BlockPos base = END_PLATFORM_BASE;
        world.getChunk(base.getX() >> 4, base.getZ() >> 4);

        BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                mutable.set(base.getX() + dx, base.getY(), base.getZ() + dz);
                world.setBlockState(mutable, obsidian);

                for (int dy = 1; dy <= 4; dy++) {
                    mutable.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    world.setBlockState(mutable, air);
                }
            }
        }

        EnderFightMod.LOGGER.debug("Ensured obsidian platform at {} in {}", base, world.getRegistryKey().getValue());
    }

    private boolean rebuildEndWorld(MinecraftServer server, ServerWorld oldWorld, RegistryKey<World> oldKey, long newSeed) {
        MutableRegistry<DimensionOptions> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry == null) {
            EnderFightMod.LOGGER.warn("Unable to access dimension registry; End seed unchanged");
            return false;
        }

        DimensionOptions template = dimensionRegistry.get(DimensionOptions.END);
        if (template == null) {
            EnderFightMod.LOGGER.warn("Dimension registry does not contain the End template; aborting reseed");
            return false;
        }

        NoiseChunkGenerator generator = createEndChunkGenerator(server, newSeed);
        if (generator == null) {
            return false;
        }

        DimensionOptions newOptions = new DimensionOptions(template.dimensionTypeEntry(), generator);
        RegistryKey<World> newWorldKey = createNextDimensionKey(newSeed);

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;

        // In-place rebuild when using a stable world key.
        if (newWorldKey.equals(oldKey)) {
            try {
                oldWorld.close();
            } catch (IOException ex) {
                EnderFightMod.LOGGER.warn("Encountered error while closing old End world", ex);
            }
            accessor.getWorlds().remove(oldKey);
            WorldSeedOverrides.removeSeedOverride(oldKey);

            // Replace the dimension definition with the new generator WITHOUT remove+add.
            // Remove+add leaves behind stale rawIdToEntry entries and causes Duplicate key crashes on shutdown.
            replaceDimensionOptionsInPlace(dimensionRegistry, oldKey, newOptions);

            // Remove old chunk data so the new seed takes effect.
            deleteWorldDirectory(server, oldKey);

            ServerWorld newWorld = instantiateEndWorld(server, accessor, oldKey, newOptions, newSeed);
            EnderFightMod.LOGGER.info("Rebuilt End world {} with requested seed {}, actual world seed: {}",
                oldKey.getValue(), newSeed, newWorld.getSeed());
            accessor.getWorlds().put(oldKey, newWorld);
            initializeEndDragonFight(newWorld);

            activeEndWorldKey = oldKey;
            return true;
        }

        // Legacy path: different keys.
        registerDimensionOptions(dimensionRegistry, newWorldKey, newOptions);

        ServerWorld newWorld = instantiateEndWorld(server, accessor, newWorldKey, newOptions, newSeed);
        EnderFightMod.LOGGER.info("Created End world {} with requested seed {}, actual world seed: {}",
            newWorldKey.getValue(), newSeed, newWorld.getSeed());
        accessor.getWorlds().put(newWorldKey, newWorld);
        initializeEndDragonFight(newWorld);

        try {
            oldWorld.close();
        } catch (IOException ex) {
            EnderFightMod.LOGGER.warn("Encountered error while closing old End world", ex);
        }
        accessor.getWorlds().remove(oldKey);
        unregisterDimensionOptions(dimensionRegistry, oldKey);
        deleteWorldDirectory(server, oldKey);
        WorldSeedOverrides.removeSeedOverride(oldKey);

        activeEndWorldKey = newWorldKey;
        return true;
    }

    private void unregisterStaleEndDimensions(MinecraftServer server, RegistryKey<World> activeKeyToKeep) {
        MutableRegistry<DimensionOptions> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry == null) {
            return;
        }

        String keepPath = null;
        if (activeKeyToKeep != null && activeKeyToKeep.getValue() != null && EnderFightMod.MOD_ID.equals(activeKeyToKeep.getValue().getNamespace())) {
            keepPath = activeKeyToKeep.getValue().getPath();
        }

        final String keepPathFinal = keepPath;

        List<RegistryKey<World>> toRemove = new ArrayList<>();
        if (dimensionRegistry instanceof SimpleRegistry<DimensionOptions> simpleRegistry) {
            @SuppressWarnings("unchecked")
            SimpleRegistryAccessor<DimensionOptions> accessor = (SimpleRegistryAccessor<DimensionOptions>) (Object) simpleRegistry;

            for (Object keyObj : accessor.getKeyToEntry().keySet()) {
                if (!(keyObj instanceof RegistryKey<?> dimKey)) {
                    continue;
                }

                Identifier id = dimKey.getValue();
                if (id == null) {
                    continue;
                }
                if (!EnderFightMod.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                String path = id.getPath();
                if (!path.startsWith("daily_end_")) {
                    continue;
                }
                if (keepPathFinal != null && keepPathFinal.equals(path)) {
                    continue;
                }
                toRemove.add(RegistryKey.of(RegistryKeys.WORLD, id));
            }
        } else {
            EnderFightMod.LOGGER.warn("Dimension registry is not a SimpleRegistry; cannot unregister stale dimension defs safely");
            return;
        }

        if (toRemove.isEmpty()) {
            return;
        }

        int removedWorlds = 0;
        int removedDimensions = 0;

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        for (RegistryKey<World> key : toRemove) {
            ServerWorld world = accessor.getWorlds().get(key);
            if (world != null) {
                try {
                    world.close();
                } catch (IOException ex) {
                    EnderFightMod.LOGGER.debug("Error closing stale End world {} during shutdown", key.getValue(), ex);
                }
                accessor.getWorlds().remove(key);
                WorldSeedOverrides.removeSeedOverride(key);
                removedWorlds++;
            }

            if (unregisterDimensionOptions(dimensionRegistry, key)) {
                removedDimensions++;
            }
        }

        // Make the DIMENSION registry internally consistent after removals.
        rebuildDimensionRegistryIndices(dimensionRegistry);

        EnderFightMod.LOGGER.info(
            "Unregistered stale Ender-Fight dimensions (candidates={}, removedWorlds={}, removedDimensionDefs={})",
            toRemove.size(),
            removedWorlds,
            removedDimensions
        );
    }

    private boolean unregisterDimensionOptions(MutableRegistry<DimensionOptions> registry, RegistryKey<World> worldKey) {
        if (registry == null || worldKey == null) {
            return false;
        }

        RegistryKey<DimensionOptions> dimensionKey = RegistryKey.of(RegistryKeys.DIMENSION, worldKey.getValue());
        if (!registry.contains(dimensionKey)) {
            return false;
        }

        if (registry instanceof SimpleRegistry<DimensionOptions> simpleRegistry) {
            @SuppressWarnings("unchecked")
            SimpleRegistryAccessor<DimensionOptions> accessor = (SimpleRegistryAccessor<DimensionOptions>) (Object) simpleRegistry;
            boolean wasFrozen = accessor.getFrozen();
            if (wasFrozen) {
                accessor.setFrozen(false);
            }
            try {
                // SimpleRegistry has no public removal API in this version; remove the entry from the backing maps.
                @SuppressWarnings("unchecked")
                Map<RegistryKey<DimensionOptions>, RegistryEntry.Reference<DimensionOptions>> keyToEntry =
                    (Map<RegistryKey<DimensionOptions>, RegistryEntry.Reference<DimensionOptions>>) (Map<?, ?>) accessor.getKeyToEntry();

                @SuppressWarnings("unchecked")
                Map<Identifier, RegistryEntry.Reference<DimensionOptions>> idToEntry =
                    (Map<Identifier, RegistryEntry.Reference<DimensionOptions>>) (Map<?, ?>) accessor.getIdToEntry();

                RegistryEntry.Reference<DimensionOptions> removed = keyToEntry.remove(dimensionKey);
                idToEntry.remove(worldKey.getValue());

                if (removed != null) {
                    DimensionOptions value = removed.value();
                    int rawId = accessor.getEntryToRawId().getInt(value);
                    accessor.getEntryToRawId().removeInt(value);
                    accessor.getValueToEntry().remove(value);
                    Map<DimensionOptions, RegistryEntry.Reference<DimensionOptions>> intrusive = accessor.getIntrusiveValueToEntry();
                    if (intrusive != null) {
                        intrusive.remove(value);
                    }
                }
            } finally {
                if (wasFrozen) {
                    accessor.setFrozen(true);
                }
            }
            return !registry.contains(dimensionKey);
        }

        // Non-SimpleRegistry implementations cannot be safely mutated here.
        return false;
    }

    private void replaceDimensionOptionsInPlace(MutableRegistry<DimensionOptions> registry,
                                                RegistryKey<World> worldKey,
                                                DimensionOptions newOptions) {
        if (registry == null || worldKey == null || newOptions == null) {
            return;
        }

        RegistryKey<DimensionOptions> dimensionKey = RegistryKey.of(RegistryKeys.DIMENSION, worldKey.getValue());
        if (!(registry instanceof SimpleRegistry<DimensionOptions> simpleRegistry)) {
            // Fallback: best-effort. (Should not happen in normal dedicated server runtime.)
            unregisterDimensionOptions(registry, worldKey);
            registerDimensionOptions(registry, worldKey, newOptions);
            return;
        }

        @SuppressWarnings("unchecked")
        SimpleRegistryAccessor<DimensionOptions> accessor = (SimpleRegistryAccessor<DimensionOptions>) (Object) simpleRegistry;
        boolean wasFrozen = accessor.getFrozen();
        if (wasFrozen) {
            accessor.setFrozen(false);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<RegistryKey<DimensionOptions>, RegistryEntry.Reference<DimensionOptions>> keyToEntry =
                (Map<RegistryKey<DimensionOptions>, RegistryEntry.Reference<DimensionOptions>>) (Map<?, ?>) accessor.getKeyToEntry();

            RegistryEntry.Reference<DimensionOptions> existing = keyToEntry.get(dimensionKey);
            if (existing == null) {
                // Not present yet.
                registerDimensionOptions(registry, worldKey, newOptions);
                return;
            }

            DimensionOptions oldValue = existing.value();

            // Update the reference value (no new entries created).
            SimpleRegistryAccessor.invokeSetValue(newOptions, existing);

            // Keep maps consistent.
            accessor.getValueToEntry().remove(oldValue);
            accessor.getValueToEntry().put(newOptions, existing);
            Map<DimensionOptions, RegistryEntry.Reference<DimensionOptions>> intrusive = accessor.getIntrusiveValueToEntry();
            if (intrusive != null) {
                intrusive.remove(oldValue);
                intrusive.put(newOptions, existing);
            }

            int rawId = accessor.getEntryToRawId().getInt(oldValue);
            accessor.getEntryToRawId().removeInt(oldValue);
            accessor.getEntryToRawId().put(newOptions, rawId);
        } finally {
            if (wasFrozen) {
                accessor.setFrozen(true);
            }
        }
    }

    private void rebuildDimensionRegistryIndices(MutableRegistry<DimensionOptions> registry) {
        if (!(registry instanceof SimpleRegistry<DimensionOptions> simpleRegistry)) {
            return;
        }

        @SuppressWarnings("unchecked")
        SimpleRegistryAccessor<DimensionOptions> accessor = (SimpleRegistryAccessor<DimensionOptions>) (Object) simpleRegistry;

        boolean wasFrozen = accessor.getFrozen();
        if (wasFrozen) {
            accessor.setFrozen(false);
        }

        try {
            // Rebuild the raw-id tables from keyToEntry, which contains exactly one entry per key.
            @SuppressWarnings("unchecked")
            Map<RegistryKey<DimensionOptions>, RegistryEntry.Reference<DimensionOptions>> keyToEntry =
                (Map<RegistryKey<DimensionOptions>, RegistryEntry.Reference<DimensionOptions>>) (Map<?, ?>) accessor.getKeyToEntry();

            ArrayList<RegistryEntry.Reference<DimensionOptions>> entries = new ArrayList<>(keyToEntry.values());
            entries.sort(Comparator.comparing(ref -> {
                if (ref == null) {
                    return "";
                }
                return String.valueOf(ref.registryKey().getValue());
            }));

            accessor.getRawIdToEntry().clear();
            accessor.getEntryToRawId().clear();
            accessor.getValueToEntry().clear();

            @SuppressWarnings("unchecked")
            Map<Identifier, RegistryEntry.Reference<DimensionOptions>> idToEntry =
                (Map<Identifier, RegistryEntry.Reference<DimensionOptions>>) (Map<?, ?>) accessor.getIdToEntry();
            idToEntry.clear();

            Map<DimensionOptions, RegistryEntry.Reference<DimensionOptions>> intrusive = accessor.getIntrusiveValueToEntry();
            if (intrusive != null) {
                intrusive.clear();
            }

            for (int rawId = 0; rawId < entries.size(); rawId++) {
                RegistryEntry.Reference<DimensionOptions> ref = entries.get(rawId);
                if (ref == null) {
                    continue;
                }

                accessor.getRawIdToEntry().add(ref);

                DimensionOptions value = ref.value();
                accessor.getEntryToRawId().put(value, rawId);
                accessor.getValueToEntry().put(value, ref);
                if (intrusive != null) {
                    intrusive.put(value, ref);
                }
                idToEntry.put(ref.registryKey().getValue(), ref);
            }
        } finally {
            if (wasFrozen) {
                accessor.setFrozen(true);
            }
        }
    }

    private NoiseChunkGenerator createEndChunkGenerator(MinecraftServer server, long seed) {
        Optional<Registry<ChunkGeneratorSettings>> settingsRegistry = server.getRegistryManager().getOptional(RegistryKeys.CHUNK_GENERATOR_SETTINGS);
        if (settingsRegistry.isEmpty()) {
            EnderFightMod.LOGGER.warn("Failed to resolve chunk generator settings for End; aborting reseed");
            return null;
        }

        Optional<RegistryEntry.Reference<ChunkGeneratorSettings>> settingsEntry = settingsRegistry.get().getEntry(ChunkGeneratorSettings.END.getValue());
        if (settingsEntry.isEmpty()) {
            EnderFightMod.LOGGER.warn("Missing End chunk generator settings entry; aborting reseed");
            return null;
        }

        RegistryWrapper.Impl<Biome> biomeLookup;
        try {
            biomeLookup = server.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        } catch (RuntimeException ex) {
            EnderFightMod.LOGGER.warn("Failed to resolve biome lookup for End; aborting reseed", ex);
            return null;
        }

        TheEndBiomeSource endBiomeSource = TheEndBiomeSource.createVanilla(biomeLookup);
        NoiseChunkGenerator generator = new NoiseChunkGenerator(endBiomeSource, settingsEntry.get());

        RegistryWrapper.Impl<DoublePerlinNoiseSampler.NoiseParameters> noiseParametersLookup;
        try {
            noiseParametersLookup = server.getRegistryManager().getOrThrow(RegistryKeys.NOISE_PARAMETERS);
        } catch (RuntimeException ex) {
            EnderFightMod.LOGGER.warn("Failed to resolve noise parameter lookup for End; terrain may repeat", ex);
            return generator;
        }

        if (((Object) generator) instanceof NoiseChunkGeneratorExtension extension) {
            NoiseConfig customConfig = NoiseConfig.create(
                settingsEntry.get().value(),
                noiseParametersLookup,
                seed
            );
            extension.endfight$setCustomNoiseConfig(customConfig);
            EnderFightMod.LOGGER.info("Applied custom noise config to End generator with seed {}", seed);
        } else {
            EnderFightMod.LOGGER.warn("NoiseChunkGenerator mixin missing; End terrain may reuse previous seed");
        }

        return generator;
    }

    private void registerDimensionOptions(MutableRegistry<DimensionOptions> registry,
                                          RegistryKey<World> worldKey,
                                          DimensionOptions options) {
        RegistryKey<DimensionOptions> dimensionKey = RegistryKey.of(RegistryKeys.DIMENSION, worldKey.getValue());
        if (registry.contains(dimensionKey)) {
            return;
        }
        if (registry instanceof SimpleRegistry<DimensionOptions> simpleRegistry) {
            @SuppressWarnings("unchecked")
            SimpleRegistryAccessor<DimensionOptions> accessor = (SimpleRegistryAccessor<DimensionOptions>) (Object) simpleRegistry;
            boolean wasFrozen = accessor.getFrozen();
            if (wasFrozen) {
                accessor.setFrozen(false);
            }
            try {
                simpleRegistry.add(dimensionKey, options, RegistryEntryInfo.DEFAULT);
            } finally {
                if (wasFrozen) {
                    accessor.setFrozen(true);
                }
            }
            return;
        }
        registry.add(dimensionKey, options, RegistryEntryInfo.DEFAULT);
    }

    private ServerWorld instantiateEndWorld(MinecraftServer server,
                                            MinecraftServerAccessor accessor,
                                            RegistryKey<World> worldKey,
                                            DimensionOptions dimensionOptions,
                                            long newSeed) {
        // CRITICAL: Set seed override BEFORE creating the world, as chunk generation starts immediately
        WorldSeedOverrides.setSeedOverride(worldKey, newSeed);
        EnderFightMod.LOGGER.info("Pre-registered seed override for {}: {}", worldKey.getValue(), newSeed);
        
        SaveProperties saveProperties = accessor.getSaveProperties();
        ServerWorldProperties mainWorldProperties = saveProperties.getMainWorldProperties();
        UnmodifiableLevelProperties derivedProperties = new UnmodifiableLevelProperties(saveProperties, mainWorldProperties);
        boolean debugWorld = saveProperties.isDebugWorld();
        long hashedSeed = BiomeAccess.hashSeed(newSeed);
        RandomSequencesState randomSequencesState = new RandomSequencesState(hashedSeed);
        List<SpecialSpawner> spawners = ImmutableList.of();

        ServerWorld world = new ServerWorld(
            server,
            accessor.getWorkerExecutor(),
            accessor.getSession(),
            derivedProperties,
            worldKey,
            dimensionOptions,
            debugWorld,
            hashedSeed,
            spawners,
            false,
            randomSequencesState
        );
        
        // CRITICAL: Set seed override BEFORE world is fully initialized
        WorldSeedOverrides.setSeedOverride(worldKey, newSeed);
        
        // Attempt to set seed via duck interface (if mixin applied)
        if (world instanceof ServerWorldDuck duck) {
            duck.enderfight$setSeed(newSeed);
            EnderFightMod.LOGGER.info("Successfully set custom seed via mixin duck interface");
        } else {
            EnderFightMod.LOGGER.warn("ServerWorld does not implement ServerWorldDuck; seed override may not be reflected in getSeed()");
        }

        ensureEndSpawnPlatform(world);
        
        // Verify seed was applied
        long actualSeed = world.getSeed();
        EnderFightMod.LOGGER.info("Created End world with requested seed: {}, actual world seed: {}", 
            newSeed, actualSeed);
        
        return world;
    }

    private RegistryKey<World> createNextDimensionKey(long seed) {
        // Use a stable custom dimension id so old dimensions do not accumulate in the world's dynamic
        // registry (which causes Minecraft to recreate empty folders every boot).
        return STABLE_END_WORLD_KEY;
    }

    private RegistryKey<World> normalizeActiveEndKey(RegistryKey<World> key) {
        if (key == null) {
            return STABLE_END_WORLD_KEY;
        }

        Identifier id = key.getValue();
        if (id == null) {
            return STABLE_END_WORLD_KEY;
        }

        if (EnderFightMod.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("daily_end_")) {
            return STABLE_END_WORLD_KEY;
        }

        return key;
    }

    private void migrateActiveEndToStableDirectory(MinecraftServer server, EndResetPersistentState state) {
        if (server == null || state == null) {
            return;
        }

        RegistryKey<World> active = state.getActiveDimensionKey();
        if (active == null || active.getValue() == null) {
            return;
        }

        Identifier id = active.getValue();
        if (!EnderFightMod.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        if (!id.getPath().startsWith("daily_end_")) {
            return;
        }
        if (STABLE_END_ID.equals(id)) {
            return;
        }

        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Path baseDir = worldRoot.resolve("dimensions").resolve(EnderFightMod.MOD_ID);
        Path from = baseDir.resolve(id.getPath());
        Path to = baseDir.resolve(STABLE_END_ID.getPath());
        if (!Files.exists(from)) {
            // Nothing to migrate on disk; still normalize the active key.
            state.setActiveDimensionKey(STABLE_END_WORLD_KEY);
            state.save(server);
            return;
        }
        if (Files.exists(to)) {
            EnderFightMod.LOGGER.warn("Stable End directory {} already exists; not migrating {}", to, from);
            state.setActiveDimensionKey(STABLE_END_WORLD_KEY);
            state.save(server);
            return;
        }

        try {
            Files.createDirectories(baseDir);
            Files.move(from, to);
            EnderFightMod.LOGGER.info("Migrated active End directory {} -> {}", from, to);
            state.setActiveDimensionKey(STABLE_END_WORLD_KEY);
            state.save(server);
        } catch (IOException ex) {
            EnderFightMod.LOGGER.warn("Failed migrating active End directory {} -> {}", from, to, ex);
            return;
        }
    }

    private MutableRegistry<DimensionOptions> locateDimensionRegistry(MinecraftServer server) {
        CombinedDynamicRegistries<ServerDynamicRegistryType> combined = server.getCombinedDynamicRegistries();
        DynamicRegistryManager.Immutable dimensionManager = combined.get(ServerDynamicRegistryType.DIMENSIONS);
        Optional<Registry<DimensionOptions>> registry = dimensionManager.getOptional(RegistryKeys.DIMENSION);
        if (registry.isEmpty()) {
            return null;
        }
        if (registry.get() instanceof MutableRegistry<DimensionOptions> mutable) {
            return mutable;
        }
        if (registry.get() instanceof SimpleRegistry<DimensionOptions> simple) {
            return simple;
        }
        return null;
    }

    private void cleanupStaleEndDimensions(MinecraftServer server, RegistryKey<World> activeKeyToKeep) {
        if (server == null) {
            return;
        }

        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Path baseDir = worldRoot.resolve("dimensions").resolve(EnderFightMod.MOD_ID);
        if (!Files.isDirectory(baseDir)) {
            return;
        }

        Path keepDir = null;
        if (activeKeyToKeep != null) {
            Identifier activeId = activeKeyToKeep.getValue();
            if (activeId != null && EnderFightMod.MOD_ID.equals(activeId.getNamespace())) {
                // Avoid DimensionType#getSaveDirectory here to reduce risk of path mismatches on Windows.
                keepDir = baseDir.resolve(activeId.getPath());
            }
        }

        final Path keepDirFinal = keepDir == null ? null : keepDir.toAbsolutePath().normalize();

        try (Stream<Path> children = Files.list(baseDir)) {
            List<Path> childrenList = children
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("daily_end_"))
                .toList();

            // If we cannot resolve the active directory reliably, do NOT delete everything.
            // Keep the newest folder instead to avoid forcing an End regen every restart.
            Path newestToKeep = null;
            if (keepDirFinal == null && !childrenList.isEmpty()) {
                newestToKeep = childrenList.stream()
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException ex) {
                            // Fall back to lexicographic ordering when timestamps are unavailable.
                            return a.getFileName().toString().compareTo(b.getFileName().toString());
                        }
                    })
                    .orElse(null);
            }

            final Path newestToKeepFinal = newestToKeep == null ? null : newestToKeep.toAbsolutePath().normalize();

            List<Path> candidates = childrenList.stream()
                .filter(path -> {
                    Path normalized = path.toAbsolutePath().normalize();
                    if (keepDirFinal != null && normalized.equals(keepDirFinal)) {
                        return false;
                    }
                    return newestToKeepFinal == null || !normalized.equals(newestToKeepFinal);
                })
                .toList();

            int deleted = 0;
            int failed = 0;
            for (Path candidate : candidates) {
                boolean success = deleteDirectoryWithRetries(candidate, 3);
                if (success) {
                    deleted++;
                } else {
                    failed++;
                }
            }

            if (!candidates.isEmpty()) {
                EnderFightMod.LOGGER.info(
                    "Pruned Ender-Fight stale dimension folders under {} (attempted={}, deleted={}, failed={})",
                    baseDir,
                    candidates.size(),
                    deleted,
                    failed
                );
            }
        } catch (IOException ex) {
            EnderFightMod.LOGGER.warn("Failed scanning Ender-Fight dimension folders under {}", baseDir, ex);
        }
    }

    private boolean deleteDirectoryWithRetries(Path directory, int attempts) {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                deleteDirectoryRecursively(directory);
                if (!Files.exists(directory)) {
                    return true;
                }
                throw new IOException("Directory still exists after deletion attempt: " + directory);
            } catch (IOException ex) {
                if (attempt >= attempts) {
                    EnderFightMod.LOGGER.error("Failed to delete directory {} after {} attempts", directory, attempts, ex);
                    return false;
                }
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return !Files.exists(directory);
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    protected void deleteWorldDirectory(MinecraftServer server, RegistryKey<World> worldKey) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Path directory = DimensionType.getSaveDirectory(worldKey, worldRoot);
        if (!Files.exists(directory)) {
            EnderFightMod.LOGGER.info("World folder {} missing (already clean)", directory);
            return;
        }

        boolean deleted = deleteDirectoryWithRetries(directory, 3);
        if (deleted && !Files.exists(directory)) {
            EnderFightMod.LOGGER.info("Deleted End dimension folder at {}", directory);
        }
    }

    @SuppressWarnings("deprecation")
    private void initializeEndDragonFight(ServerWorld endWorld) {
        if (endWorld.getEnderDragonFight() == null) {
            EnderFightMod.LOGGER.info("Initializing ender dragon fight for {}", endWorld.getRegistryKey().getValue());
            EnderDragonFight dragonFight = new EnderDragonFight(endWorld, endWorld.getSeed(), EnderDragonFight.Data.DEFAULT);
            dragonFight.setSkipChunksLoadedCheck();
            endWorld.setEnderDragonFight(dragonFight);
            dragonFight.respawnDragon();
            EnderFightMod.LOGGER.info("Requested dragon respawn for {}", endWorld.getRegistryKey().getValue());
        }
    }

    private boolean hasPendingServerPortalsHandoff(ServerPlayerEntity player) {
        try {
            Class<?> modClass = Class.forName("de.michiruf.serverportals.ServerPortalsMod");
            Method hasPending = modClass.getMethod("hasPendingPortalTeleport", java.util.UUID.class);
            Object result = hasPending.invoke(null, player.getUuid());
            if (result instanceof Boolean booleanResult) {
                return booleanResult;
            }
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            EnderFightMod.LOGGER.debug("Unable to query ServerPortals handoff for {}: {}",
                player.getName().getString(), e.getMessage());
        }
        return false;
    }
}