package com.silver.enderfight.reset;

import com.google.common.collect.ImmutableList;
import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
import com.silver.enderfight.dragon.DragonBreathModifier;
import com.silver.enderfight.duck.NoiseChunkGeneratorExtension;
import com.silver.enderfight.duck.ServerWorldDuck;
import com.silver.enderfight.mixin.MinecraftServerAccessor;
import com.silver.enderfight.mixin.HolderReferenceAccessor;
import com.silver.enderfight.mixin.SimpleRegistryAccessor;
import com.silver.enderfight.portal.PortalInterceptor;
import com.silver.enderfight.util.WorldSeedOverrides;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec3;
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
    private static final int ACTION_BAR_UPDATE_INTERVAL_TICKS = 20 * 60;
    private static final DateTimeFormatter DIMENSION_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String DRAGON_BOSS_BAR_FIELD_NAME = "field_13119"; // obfuscated getter for EnderDragonFight#bossBar

    private static final Identifier STABLE_END_ID = Identifier.fromNamespaceAndPath(EnderFightMod.MOD_ID, "daily_end_active");
    private static final ResourceKey<Level> STABLE_END_WORLD_KEY = ResourceKey.create(Registries.DIMENSION, STABLE_END_ID);
    private static final TagKey<Biome> END_BIOME_TAG = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "is_end"));

    private final ConfigManager configManager;

    private EndControlConfig cachedConfig;
    private EndResetPersistentState persistentState;
    private boolean countdownActive;
    private long countdownTicksRemaining;
    private boolean warningSent;
    private ResourceKey<Level> activeEndWorldKey = Level.END;
    private int wallClockCheckAccumulator;
    private boolean resetIntervalElapsed;
    private boolean warningWindowExceeded;
    private long lastActionBarBucket = Long.MIN_VALUE;
    private boolean lastActionBarSecondMode;
    private Component lastActionBarMessage;

    private static final int VANILLA_END_REDIRECT_DELAY_TICKS = 1;
    private final Map<UUID, Integer> pendingVanillaEndRedirects = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceKey<Level>> observedPlayerWorlds = new ConcurrentHashMap<>();

    private static final BlockPos END_PLATFORM_BASE = new BlockPos(100, 49, 0);
    private static final Vec3 END_PLATFORM_SPAWN = Vec3.atCenterOf(END_PLATFORM_BASE).add(0.0, 1.0, 0.0);
    private static final float END_PLATFORM_YAW = 180.0F;
    private static java.lang.reflect.Field dragonBossBarField;

    public EndResetManager(ConfigManager configManager) {
        this.configManager = configManager;
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

        ResourceKey<Level> activeKeyToKeep = state.getActiveDimensionKey();

        // If any prior build produced duplicate entries in the DIMENSION registry's raw-id tables,
        // Minecraft can crash on shutdown while serializing registries. Rebuild the tables up front.
        WritableRegistry<LevelStem> dimensionRegistry = locateDimensionRegistry(server);
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

        ResourceKey<Level> activeKeyToKeep = activeEndWorldKey;
        if (persistentState != null) {
            activeKeyToKeep = persistentState.getActiveDimensionKey();
            persistentState.save(server);
        }

        // Remove old dimension definitions and loaded worlds so Minecraft does not keep recreating
        // empty daily_end_* folders and trying to save them.
        unregisterStaleEndDimensions(server, activeKeyToKeep);
    }

    public void onServerStopped(MinecraftServer server) {
        ResourceKey<Level> activeKeyToKeep = activeEndWorldKey;
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
        this.activeEndWorldKey = Level.END;
        this.lastActionBarBucket = Long.MIN_VALUE;
        this.lastActionBarSecondMode = false;
        this.lastActionBarMessage = null;
    }

    public void tick(MinecraftServer server) {
        if (persistentState == null) {
            return;
        }

        cachedConfig = configManager.getConfig();

        observePlayerWorldChanges(server);
        processPendingVanillaEndRedirects(server);

        updateResetScheduleFlags();

        if (!countdownActive && resetIntervalElapsed) {
            beginCountdown();
        }

        sendEndResetActionBar(server);

        if (!countdownActive) {
            return;
        }

        if (warningWindowExceeded && countdownTicksRemaining > 0) {
            EnderFightMod.LOGGER.info("Reset overdue while server was idle; fast-forwarding countdown ({} ticks remaining)", countdownTicksRemaining);
            countdownTicksRemaining = 0;
        }

        processCountdown(server);
    }

    private void sendEndResetActionBar(MinecraftServer server) {
        if (server == null) {
            return;
        }

        List<ServerPlayer> endPlayers = server.getPlayerList().getPlayers().stream()
            .filter(this::isPlayerInEndContext)
            .collect(Collectors.toList());
        if (endPlayers.isEmpty()) {
            return;
        }

        refreshActionBarMessageIfNeeded();
        if (lastActionBarMessage == null) {
            return;
        }

        for (ServerPlayer player : endPlayers) {
            player.sendSystemMessage(lastActionBarMessage, true);
        }
    }

    private void sendResetActionBarToPlayer(ServerPlayer player) {
        if (!isPlayerInEndContext(player)) {
            return;
        }

        refreshActionBarMessageIfNeeded();
        if (lastActionBarMessage == null) {
            return;
        }

        player.sendSystemMessage(lastActionBarMessage, true);
    }

    private void refreshActionBarMessageIfNeeded() {
        long remainingMillis = getRemainingMillisUntilReset();
        long remainingSeconds = Math.max(0L, (remainingMillis + 999L) / 1000L);
        boolean secondMode = remainingSeconds < 60L;
        long bucket = secondMode ? remainingSeconds : Math.max(0L, (remainingSeconds + 59L) / 60L);

        if (bucket == lastActionBarBucket && secondMode == lastActionBarSecondMode && lastActionBarMessage != null) {
            return;
        }

        String timeRemaining = formatRemainingTime(remainingMillis);
        lastActionBarMessage = Component.literal("End resets in " + timeRemaining);
        lastActionBarBucket = bucket;
        lastActionBarSecondMode = secondMode;
    }

    private long getRemainingMillisUntilReset() {
        if (persistentState == null) {
            return 0L;
        }

        if (cachedConfig == null) {
            cachedConfig = configManager.getConfig();
        }

        if (countdownActive) {
            return Math.max(0L, countdownTicksRemaining * 50L);
        }

        long lastReset = persistentState.getLastResetEpochMillis();
        long warningMillis = Math.max(0L, cachedConfig.warningDelayTicks()) * 50L;
        long intervalMillis = (long) Math.max(1D, cachedConfig.resetIntervalHours() * 3_600_000D);

        if (lastReset <= 0L) {
            return warningMillis;
        }

        long targetResetMillis = lastReset + intervalMillis + warningMillis;
        return Math.max(0L, targetResetMillis - Instant.now().toEpochMilli());
    }

    private boolean isPlayerInEndContext(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        ServerLevel world = player.createCommandSourceStack().getLevel();
        if (world == null) {
            return false;
        }

        if (PortalInterceptor.isManagedEndDimension(world.dimension())) {
            return true;
        }

        Identifier dimensionId = world.dimension().identifier();
        if (dimensionId != null && dimensionId.getPath().contains("end")) {
            return true;
        }

        return isEndBiome(world, player.blockPosition());
    }

    private boolean isEndBiome(ServerLevel world, BlockPos position) {
        if (world == null || position == null) {
            return false;
        }

        Holder<Biome> biome = world.getBiome(position);
        return biome.is(END_BIOME_TAG);
    }

    private String formatRemainingTime(long remainingMillis) {
        if (remainingMillis < 60_000L) {
            long seconds = (remainingMillis + 999L) / 1000L;
            return seconds + "s";
        }

        long totalMinutes = (remainingMillis + 59_999L) / 60_000L;
        if (totalMinutes <= 0L) {
            return "0m";
        }

        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        if (hours > 0 && minutes > 0) {
            return hours + "h " + minutes + "m";
        }

        if (hours > 0) {
            return hours + "h";
        }

        return minutes + "m";
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

        boolean success = performReset(server, true);
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
        ServerLevel endWorld = getActiveEndWorld(server);
        if (endWorld == null) {
            return;
        }

        Component message = Component.literal(cachedConfig.warningMessage());
        for (ServerPlayer player : endWorld.players()) {
            player.sendSystemMessage(message, false);
        }
        EnderFightMod.LOGGER.info("Warned {} players about upcoming End reset", endWorld.players().size());
    }

    private boolean performReset(MinecraftServer server, boolean alignToExpectedSchedule) {
        if (server == null) {
            EnderFightMod.LOGGER.warn("Cannot reset End – server reference was null");
            return false;
        }
        ServerLevel endWorld = getActiveEndWorld(server);
        if (endWorld == null) {
            EnderFightMod.LOGGER.warn("Could not acquire End world to reset; aborting");
            return false;
        }

        List<ServerPlayer> playersInEnd = new ArrayList<>(endWorld.players());
        if (playersInEnd.isEmpty()) {
            EnderFightMod.LOGGER.info("Resetting {} – no players detected in End dimension {}; skipping teleport", endWorld.dimension().identifier(), endWorld.dimension());
        } else {
            String names = playersInEnd.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
            EnderFightMod.LOGGER.info("Resetting {} – teleporting players out: {}", endWorld.dimension().identifier(), names);
            teleportPlayersToOverworld(server, playersInEnd);
        }

        long newSeed = ThreadLocalRandom.current().nextLong();
        boolean rebuilt = rebuildEndWorld(server, endWorld, endWorld.dimension(), newSeed);
        if (!rebuilt) {
            EnderFightMod.LOGGER.warn("End reset aborted after failing to rebuild the dimension");
            return false;
        }

        ensureDragonFightState(server);

        long recordedResetTime = resolveRecordedResetTime(alignToExpectedSchedule);
        persistentState.updateOnReset(recordedResetTime, newSeed, activeEndWorldKey);
        persistentState.save(server);
        EnderFightMod.LOGGER.info("End reset completed; new seed {} recorded (recordedResetEpochMillis={})", newSeed, recordedResetTime);
        return true;
    }

    private long resolveRecordedResetTime(boolean alignToExpectedSchedule) {
        long now = Instant.now().toEpochMilli();
        if (!alignToExpectedSchedule || persistentState == null || cachedConfig == null) {
            return now;
        }

        long previousReset = persistentState.getLastResetEpochMillis();
        if (previousReset <= 0L) {
            return now;
        }

        long intervalMillis = (long) Math.max(1D, cachedConfig.resetIntervalHours() * 3_600_000D);
        long warningMillis = Math.max(0L, cachedConfig.warningDelayTicks()) * 50L;
        long cycleMillis = Math.max(1L, intervalMillis + warningMillis);
        long elapsed = Math.max(0L, now - previousReset);

        long completedCycles = Math.max(1L, elapsed / cycleMillis);
        long scheduledResetTime = previousReset + completedCycles * cycleMillis;
        return Math.min(now, scheduledResetTime);
    }

    private void ensureDragonFightState(MinecraftServer server) {
        ServerLevel endWorld = getActiveEndWorld(server);
        if (endWorld == null) {
            return;
        }
        EnderDragonFight fight = endWorld.getDragonFight();
        if (fight == null) {
            return;
        }

        fight.tryRespawn();
        EnderFightMod.LOGGER.info("Forced EnderDragonFight respawn to regenerate exit portal and gateway state");
    }

    public boolean triggerManualReset(MinecraftServer server) {
        if (persistentState == null) {
            EnderFightMod.LOGGER.warn("Manual reset requested before persistent state initialised; ignoring");
            return false;
        }
        cachedConfig = configManager.getConfig();
        boolean success = performReset(server, false);
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
        lastActionBarBucket = Long.MIN_VALUE;
        lastActionBarSecondMode = false;
        lastActionBarMessage = null;
    }

    /**
     * Collects all players currently residing in the End dimension. Exposed for unit tests and mixin hooks.
     */
    protected List<ServerPlayer> detectPlayersInEnd(ServerLevel endWorld) {
        return Collections.unmodifiableList(new ArrayList<>(endWorld.players()));
    }

    /**
     * Teleports the supplied players back to the overworld spawn using FabricDimensions.
     */
    protected void teleportPlayersToOverworld(MinecraftServer server, List<ServerPlayer> players) {
        Component notification = Component.literal(cachedConfig.teleportMessage());
        teleportPlayersToOverworld(server, players, notification, "End reset");
    }

    public void teleportPlayerToOverworld(ServerPlayer player, Component message, String logContext) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.createCommandSourceStack().getServer();
        if (server == null) {
            return;
        }

        teleportPlayersToOverworld(server, java.util.List.of(player), message, logContext);
    }

    private void teleportPlayersToOverworld(MinecraftServer server, List<ServerPlayer> players, Component message, String logContext) {
        if (players.isEmpty()) {
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            EnderFightMod.LOGGER.error("Overworld missing while attempting to teleport End players");
            return;
        }

        MinecraftServer ownerServer = overworld.getServer();
        LevelData.RespawnData spawnPoint = ownerServer != null ? ownerServer.getRespawnData() : null;
        if (spawnPoint == null) {
            spawnPoint = overworld.getLevelData().getRespawnData();
        }

    BlockPos spawnPos = spawnPoint != null ? spawnPoint.pos() : BlockPos.ZERO;
    Vec3 spawnVec = Vec3.atCenterOf(spawnPos);
        float spawnYaw = 0.0F;
        float spawnPitch = 0.0F;
        if (spawnPoint != null) {
            // Reflectively read yaw/pitch so we preserve configured spawn orientation even when mappings lack named helpers.
            try {
                spawnYaw = (float) LevelData.RespawnData.class.getMethod("yaw").invoke(spawnPoint);
                spawnPitch = (float) LevelData.RespawnData.class.getMethod("pitch").invoke(spawnPoint);
            } catch (ReflectiveOperationException ex) {
                EnderFightMod.LOGGER.debug("Unable to read spawn yaw/pitch from SpawnPoint record", ex);
            }
        }

        if (spawnPoint != null && !Level.OVERWORLD.equals(spawnPoint.dimension())) {
            EnderFightMod.LOGGER.warn("Server spawn point dimension {} differs from overworld; using position {} regardless", spawnPoint.dimension().identifier(), spawnPos);
        }

        for (ServerPlayer player : players) {
            if (ownerServer != null) {
                ownerServer.getCustomBossEvents().onPlayerDisconnect(player);
                EnderFightMod.LOGGER.info("Cleared boss bars for {} via disconnect hook", player.getName().getString());
            }

            tryRemoveFromDragonBossBar(player);

            PortalInterceptor.suppressNextRedirect(player);
            TeleportTransition target = new TeleportTransition(overworld, spawnVec, Vec3.ZERO, spawnYaw, spawnPitch, TeleportTransition.DO_NOTHING);
            player.teleport(target);
            if (message != null) {
                player.sendSystemMessage(message, false);
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

    private void handlePlayerWorldChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (activeEndWorldKey == null || activeEndWorldKey.equals(Level.END)) {
            return;
        }
        if (!destination.dimension().equals(Level.END)) {
            return;
        }
        if (PortalInterceptor.isManagedEndDimension(origin.dimension())) {
            return;
        }
        MinecraftServer server = destination.getServer();
        if (server == null) {
            return;
        }
        ServerLevel targetWorld = server.getLevel(activeEndWorldKey);
        if (targetWorld == null || targetWorld == destination) {
            return;
        }

        UUID playerId = player.getUUID();
        pendingVanillaEndRedirects.putIfAbsent(playerId, VANILLA_END_REDIRECT_DELAY_TICKS);
        EnderFightMod.LOGGER.info(
            "Queued player {} for vanilla End -> custom End redirect in {} tick(s) (target={})",
            player.getName().getString(),
            VANILLA_END_REDIRECT_DELAY_TICKS,
            activeEndWorldKey.identifier()
        );
    }

    /**
     * Fabric's entity world-change callback was removed from the 26.2 API. Polling the small player
     * list once per server tick preserves the same semantics without depending on an internal hook.
     */
    private void observePlayerWorldChanges(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel destination = player.level();
            ResourceKey<Level> destinationKey = destination.dimension();
            ResourceKey<Level> originKey = observedPlayerWorlds.put(player.getUUID(), destinationKey);
            if (originKey == null || originKey.equals(destinationKey)) {
                continue;
            }

            ServerLevel origin = server.getLevel(originKey);
            if (origin != null) {
                PortalInterceptor.handlePortalTeleport(player, originKey, destinationKey, configManager.getConfig());
                handlePlayerWorldChange(player, origin, destination);
            }
        }

        observedPlayerWorlds.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
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

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }

            ServerLevel currentWorld = player.createCommandSourceStack().getLevel();
            if (currentWorld == null || !Level.END.equals(currentWorld.dimension())) {
                continue;
            }

            if (activeEndWorldKey == null || activeEndWorldKey.equals(Level.END)) {
                continue;
            }

            ServerLevel targetWorld = server.getLevel(activeEndWorldKey);
            if (targetWorld == null || targetWorld == currentWorld) {
                continue;
            }

            EnderFightMod.LOGGER.info(
                "Redirecting player {} from vanilla End into custom End {} (delayed)",
                player.getName().getString(),
                activeEndWorldKey.identifier()
            );

            ensureEndSpawnPlatform(targetWorld);
            TeleportTransition target = new TeleportTransition(targetWorld, END_PLATFORM_SPAWN, Vec3.ZERO, END_PLATFORM_YAW, 0.0F, TeleportTransition.DO_NOTHING);
            player.teleport(target);
            sendResetActionBarToPlayer(player);
        }
    }

    private void handlePlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.player;
        observedPlayerWorlds.put(player.getUUID(), player.level().dimension());

        DragonBreathModifier.purgeExtraSpecialDragonBreath(player, "player join");

        if (persistentState == null) {
            return;
        }

        ResourceKey<Level> recordedKey = persistentState.getRecordedEndDimension(player.getUUID());
        if (recordedKey == null) {
            EnderFightMod.LOGGER.debug("No offline End record for {}; skipping safeguard", player.getName().getString());
            sendResetActionBarToPlayer(player);
            return;
        }

        EnderFightMod.LOGGER.info("Offline End record found for {}: recordedKey={} activeKey={}",
            player.getName().getString(),
            recordedKey.identifier(),
            getActiveEndWorldKey().identifier());

    ResourceKey<Level> activeKey = getActiveEndWorldKey();

        ServerLevel currentWorld = player.createCommandSourceStack().getLevel();
        if (currentWorld != null && !PortalInterceptor.isManagedEndDimension(currentWorld.dimension())) {
            EnderFightMod.LOGGER.info("Player {} already placed in {} after End reset; forcing return to spawn",
                player.getName().getString(), currentWorld.dimension().identifier());
            DragonBreathModifier.purgeExtraSpecialDragonBreath(player, "offline End reset (post-login spawn correction)");
            Component message = Component.literal("The End reset while you were offline; you've been returned to spawn.");
            teleportPlayersToOverworld(server, ImmutableList.of(player), message, "Offline End reset safeguard (post-login)");
            if (persistentState.clearRecordedPlayer(player.getUUID())) {
                persistentState.save(server);
            }
            return;
        }

        if (!PortalInterceptor.isManagedEndDimension(recordedKey) || recordedKey.equals(activeKey)) {
            EnderFightMod.LOGGER.info("Clearing offline End record for {} because recordedKey managed={} equalsActive={}",
                player.getName().getString(),
                PortalInterceptor.isManagedEndDimension(recordedKey),
                recordedKey.equals(activeKey));
            if (persistentState.clearRecordedPlayer(player.getUUID())) {
                persistentState.save(server);
            }
            return;
        }

        if (hasPendingServerPortalsHandoff(player)) {
            EnderFightMod.LOGGER.info("Skipping offline End safeguard for {} – pending ServerPortals handoff detected",
                player.getName().getString());
            if (persistentState.clearRecordedPlayer(player.getUUID())) {
                persistentState.save(server);
            }
            return;
        }

        EnderFightMod.LOGGER.info("Executing offline End safeguard for {} (recorded {}, active {})",
            player.getName().getString(), recordedKey.identifier(), activeKey.identifier());
        Component message = Component.literal("The End reset while you were offline; you've been returned to spawn.");
        DragonBreathModifier.purgeExtraSpecialDragonBreath(player, "offline End reset teleport");
        teleportPlayersToOverworld(server, ImmutableList.of(player), message, "Offline End reset safeguard");

        if (persistentState.clearRecordedPlayer(player.getUUID())) {
            persistentState.save(server);
        }
    }

    private void handlePlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        if (persistentState == null) {
            return;
        }

        ServerPlayer player = handler.player;
        observedPlayerWorlds.remove(player.getUUID());
        pendingVanillaEndRedirects.remove(player.getUUID());
        ResourceKey<Level> worldKey = player.createCommandSourceStack().getLevel().dimension();

        boolean managedEnd = PortalInterceptor.isManagedEndDimension(worldKey);
        EnderFightMod.LOGGER.info("Player {} disconnecting from world {} (managedEnd={})",
            player.getName().getString(), worldKey.identifier(), managedEnd);

        tryRemoveFromDragonBossBar(player);

        boolean changed;
        if (managedEnd) {
            persistentState.recordPlayerLoggedOutInEnd(player.getUUID(), worldKey);
            EnderFightMod.LOGGER.info("Recorded {} as offline-in-End for {}", player.getName().getString(), worldKey.identifier());
            changed = true;
        } else {
            changed = persistentState.clearRecordedPlayer(player.getUUID());
            if (changed) {
                EnderFightMod.LOGGER.info("Cleared offline End record for {} after disconnect outside End", player.getName().getString());
            }
        }

        if (changed) {
            persistentState.save(server);
        }
    }

    private void tryRemoveFromDragonBossBar(ServerPlayer player) {
        ServerLevel world = player.createCommandSourceStack().getLevel();
        if (world == null) {
            return;
        }
        EnderDragonFight fight = world.getDragonFight();
        if (fight == null) {
            return;
        }

        ServerBossEvent bossBar = resolveDragonBossBar(fight);
        if (bossBar == null) {
            return;
        }

        if (bossBar.getPlayers().contains(player)) {
            bossBar.removePlayer(player);
            EnderFightMod.LOGGER.info("Removed {} from Ender dragon boss bar via reflection", player.getName().getString());
        }
    }

    private ServerBossEvent resolveDragonBossBar(EnderDragonFight fight) {
        if (fight == null) {
            return null;
        }

        try {
            if (dragonBossBarField == null) {
                dragonBossBarField = EnderDragonFight.class.getDeclaredField(DRAGON_BOSS_BAR_FIELD_NAME);
                dragonBossBarField.setAccessible(true);
            }
            Object value = dragonBossBarField.get(fight);
            if (value instanceof ServerBossEvent bossBar) {
                return bossBar;
            }
        } catch (ReflectiveOperationException ex) {
            EnderFightMod.LOGGER.debug("Unable to access EnderDragonFight#bossBar via reflection", ex);
        }
        return null;
    }

    public ResourceKey<Level> getActiveEndWorldKey() {
        return activeEndWorldKey == null ? Level.END : activeEndWorldKey;
    }

    public ServerLevel getActiveEndWorld(MinecraftServer server) {
        return server.getLevel(getActiveEndWorldKey());
    }

    private void ensureActiveEndWorld(MinecraftServer server) {
        if (activeEndWorldKey == null) {
            activeEndWorldKey = Level.END;
        }
        if (activeEndWorldKey.equals(Level.END)) {
            return;
        }
        ServerLevel existingWorld = server.getLevel(activeEndWorldKey);
        if (existingWorld != null) {
            EnderFightMod.LOGGER.debug("Existing End world {} detected on startup; ensuring spawn platform and dragon fight", activeEndWorldKey.identifier());
            ensureEndSpawnPlatform(existingWorld);
            initializeEnderDragonFight(existingWorld);
            return;
        }

        WritableRegistry<LevelStem> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry == null) {
            EnderFightMod.LOGGER.warn("Unable to locate dimension registry; falling back to vanilla End");
            activeEndWorldKey = Level.END;
            return;
        }

        LevelStem template = dimensionRegistry.getValue(LevelStem.END);
        if (template == null) {
            EnderFightMod.LOGGER.warn("Missing template End dimension options; unable to restore custom End");
            activeEndWorldKey = Level.END;
            return;
        }

        long seed = persistentState.getCurrentEndSeed();
        NoiseBasedChunkGenerator generator = createEndChunkGenerator(server, seed);
        if (generator == null) {
            activeEndWorldKey = Level.END;
            return;
        }

        LevelStem options = new LevelStem(template.type(), generator);
        registerDimensionOptions(dimensionRegistry, activeEndWorldKey, options);

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        ServerLevel newWorld = instantiateEndWorld(server, accessor, activeEndWorldKey, options, seed);
        accessor.getWorlds().put(activeEndWorldKey, newWorld);
        initializeEnderDragonFight(newWorld);
    }

    public BlockPos getEndSpawnPlatformBase() {
        return END_PLATFORM_BASE;
    }

    public Vec3 getEndSpawnLocation() {
        return END_PLATFORM_SPAWN;
    }

    public float getEndSpawnYaw() {
        return END_PLATFORM_YAW;
    }

    public void ensureEndSpawnPlatform(ServerLevel world) {
        BlockPos base = END_PLATFORM_BASE;
        world.getChunk(base.getX() >> 4, base.getZ() >> 4);

        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                mutable.set(base.getX() + dx, base.getY(), base.getZ() + dz);
                world.setBlockAndUpdate(mutable, obsidian);

                for (int dy = 1; dy <= 4; dy++) {
                    mutable.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    world.setBlockAndUpdate(mutable, air);
                }
            }
        }

        EnderFightMod.LOGGER.debug("Ensured obsidian platform at {} in {}", base, world.dimension().identifier());
    }

    private boolean rebuildEndWorld(MinecraftServer server, ServerLevel oldWorld, ResourceKey<Level> oldKey, long newSeed) {
        WritableRegistry<LevelStem> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry == null) {
            EnderFightMod.LOGGER.warn("Unable to access dimension registry; End seed unchanged");
            return false;
        }

        LevelStem template = dimensionRegistry.getValue(LevelStem.END);
        if (template == null) {
            EnderFightMod.LOGGER.warn("Dimension registry does not contain the End template; aborting reseed");
            return false;
        }

        NoiseBasedChunkGenerator generator = createEndChunkGenerator(server, newSeed);
        if (generator == null) {
            return false;
        }

        LevelStem newOptions = new LevelStem(template.type(), generator);
        ResourceKey<Level> newWorldKey = createNextDimensionKey(newSeed);

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

            ServerLevel newWorld = instantiateEndWorld(server, accessor, oldKey, newOptions, newSeed);
            EnderFightMod.LOGGER.info("Rebuilt End world {} with requested seed {}, actual world seed: {}",
                oldKey.identifier(), newSeed, newWorld.getSeed());
            accessor.getWorlds().put(oldKey, newWorld);
            initializeEnderDragonFight(newWorld);

            activeEndWorldKey = oldKey;
            return true;
        }

        // Legacy path: different keys.
        registerDimensionOptions(dimensionRegistry, newWorldKey, newOptions);

        ServerLevel newWorld = instantiateEndWorld(server, accessor, newWorldKey, newOptions, newSeed);
        EnderFightMod.LOGGER.info("Created End world {} with requested seed {}, actual world seed: {}",
            newWorldKey.identifier(), newSeed, newWorld.getSeed());
        accessor.getWorlds().put(newWorldKey, newWorld);
        initializeEnderDragonFight(newWorld);

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

    private void unregisterStaleEndDimensions(MinecraftServer server, ResourceKey<Level> activeKeyToKeep) {
        WritableRegistry<LevelStem> dimensionRegistry = locateDimensionRegistry(server);
        if (dimensionRegistry == null) {
            return;
        }

        String keepPath = null;
        if (activeKeyToKeep != null && activeKeyToKeep.identifier() != null && EnderFightMod.MOD_ID.equals(activeKeyToKeep.identifier().getNamespace())) {
            keepPath = activeKeyToKeep.identifier().getPath();
        }

        final String keepPathFinal = keepPath;

        List<ResourceKey<Level>> toRemove = new ArrayList<>();
        if (dimensionRegistry instanceof MappedRegistry<LevelStem> simpleRegistry) {
            @SuppressWarnings("unchecked")
            SimpleRegistryAccessor<LevelStem> accessor = (SimpleRegistryAccessor<LevelStem>) (Object) simpleRegistry;

            for (Object keyObj : accessor.getKeyToEntry().keySet()) {
                if (!(keyObj instanceof ResourceKey<?> dimKey)) {
                    continue;
                }

                Identifier id = dimKey.identifier();
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
                toRemove.add(ResourceKey.create(Registries.DIMENSION, id));
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
        for (ResourceKey<Level> key : toRemove) {
            ServerLevel world = accessor.getWorlds().get(key);
            if (world != null) {
                try {
                    world.close();
                } catch (IOException ex) {
                    EnderFightMod.LOGGER.debug("Error closing stale End world {} during shutdown", key.identifier(), ex);
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

    private boolean unregisterDimensionOptions(WritableRegistry<LevelStem> registry, ResourceKey<Level> worldKey) {
        if (registry == null || worldKey == null) {
            return false;
        }

        ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, worldKey.identifier());
        if (!registry.containsKey(dimensionKey)) {
            return false;
        }

        if (registry instanceof MappedRegistry<LevelStem> simpleRegistry) {
            @SuppressWarnings("unchecked")
            SimpleRegistryAccessor<LevelStem> accessor = (SimpleRegistryAccessor<LevelStem>) (Object) simpleRegistry;
            boolean wasFrozen = accessor.getFrozen();
            if (wasFrozen) {
                accessor.setFrozen(false);
            }
            try {
                // SimpleRegistry has no public removal API in this version; remove the entry from the backing maps.
                @SuppressWarnings("unchecked")
                Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>> keyToEntry =
                    (Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>>) (Map<?, ?>) accessor.getKeyToEntry();

                @SuppressWarnings("unchecked")
                Map<Identifier, Holder.Reference<LevelStem>> idToEntry =
                    (Map<Identifier, Holder.Reference<LevelStem>>) (Map<?, ?>) accessor.getIdToEntry();

                Holder.Reference<LevelStem> removed = keyToEntry.remove(dimensionKey);
                idToEntry.remove(worldKey.identifier());

                if (removed != null) {
                    LevelStem value = removed.value();
                    int rawId = accessor.getEntryToRawId().getInt(value);
                    accessor.getEntryToRawId().removeInt(value);
                    accessor.getValueToEntry().remove(value);
                    Map<LevelStem, Holder.Reference<LevelStem>> intrusive = accessor.getIntrusiveValueToEntry();
                    if (intrusive != null) {
                        intrusive.remove(value);
                    }
                }
            } finally {
                if (wasFrozen) {
                    accessor.setFrozen(true);
                }
            }
            return !registry.containsKey(dimensionKey);
        }

        // Non-SimpleRegistry implementations cannot be safely mutated here.
        return false;
    }

    private void replaceDimensionOptionsInPlace(WritableRegistry<LevelStem> registry,
                                                ResourceKey<Level> worldKey,
                                                LevelStem newOptions) {
        if (registry == null || worldKey == null || newOptions == null) {
            return;
        }

        ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, worldKey.identifier());
        if (!(registry instanceof MappedRegistry<LevelStem> simpleRegistry)) {
            // Fallback: best-effort. (Should not happen in normal dedicated server runtime.)
            unregisterDimensionOptions(registry, worldKey);
            registerDimensionOptions(registry, worldKey, newOptions);
            return;
        }

        @SuppressWarnings("unchecked")
        SimpleRegistryAccessor<LevelStem> accessor = (SimpleRegistryAccessor<LevelStem>) (Object) simpleRegistry;
        boolean wasFrozen = accessor.getFrozen();
        if (wasFrozen) {
            accessor.setFrozen(false);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>> keyToEntry =
                (Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>>) (Map<?, ?>) accessor.getKeyToEntry();

            Holder.Reference<LevelStem> existing = keyToEntry.get(dimensionKey);
            if (existing == null) {
                // Not present yet.
                registerDimensionOptions(registry, worldKey, newOptions);
                return;
            }

            LevelStem oldValue = existing.value();

            // Update the reference value (no new entries created).
            ((HolderReferenceAccessor<LevelStem>) (Object) existing).enderfight$bindValue(newOptions);

            // Keep maps consistent.
            accessor.getValueToEntry().remove(oldValue);
            accessor.getValueToEntry().put(newOptions, existing);
            Map<LevelStem, Holder.Reference<LevelStem>> intrusive = accessor.getIntrusiveValueToEntry();
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

    private void rebuildDimensionRegistryIndices(WritableRegistry<LevelStem> registry) {
        if (!(registry instanceof MappedRegistry<LevelStem> simpleRegistry)) {
            return;
        }

        @SuppressWarnings("unchecked")
        SimpleRegistryAccessor<LevelStem> accessor = (SimpleRegistryAccessor<LevelStem>) (Object) simpleRegistry;

        boolean wasFrozen = accessor.getFrozen();
        if (wasFrozen) {
            accessor.setFrozen(false);
        }

        try {
            // Rebuild the raw-id tables from keyToEntry, which contains exactly one entry per key.
            @SuppressWarnings("unchecked")
            Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>> keyToEntry =
                (Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>>) (Map<?, ?>) accessor.getKeyToEntry();

            ArrayList<Holder.Reference<LevelStem>> entries = new ArrayList<>(keyToEntry.values());
            entries.sort(Comparator.comparing(ref -> {
                if (ref == null) {
                    return "";
                }
                return String.valueOf(ref.key().identifier());
            }));

            accessor.getRawIdToEntry().clear();
            accessor.getEntryToRawId().clear();
            accessor.getValueToEntry().clear();

            @SuppressWarnings("unchecked")
            Map<Identifier, Holder.Reference<LevelStem>> idToEntry =
                (Map<Identifier, Holder.Reference<LevelStem>>) (Map<?, ?>) accessor.getIdToEntry();
            idToEntry.clear();

            Map<LevelStem, Holder.Reference<LevelStem>> intrusive = accessor.getIntrusiveValueToEntry();
            if (intrusive != null) {
                intrusive.clear();
            }

            for (int rawId = 0; rawId < entries.size(); rawId++) {
                Holder.Reference<LevelStem> ref = entries.get(rawId);
                if (ref == null) {
                    continue;
                }

                accessor.getRawIdToEntry().add(ref);

                LevelStem value = ref.value();
                accessor.getEntryToRawId().put(value, rawId);
                accessor.getValueToEntry().put(value, ref);
                if (intrusive != null) {
                    intrusive.put(value, ref);
                }
                idToEntry.put(ref.key().identifier(), ref);
            }
        } finally {
            if (wasFrozen) {
                accessor.setFrozen(true);
            }
        }
    }

    private NoiseBasedChunkGenerator createEndChunkGenerator(MinecraftServer server, long seed) {
        Optional<Registry<NoiseGeneratorSettings>> settingsRegistry = server.registryAccess().lookup(Registries.NOISE_SETTINGS);
        if (settingsRegistry.isEmpty()) {
            EnderFightMod.LOGGER.warn("Failed to resolve chunk generator settings for End; aborting reseed");
            return null;
        }

        Optional<Holder.Reference<NoiseGeneratorSettings>> settingsEntry = settingsRegistry.get().get(NoiseGeneratorSettings.END.identifier());
        if (settingsEntry.isEmpty()) {
            EnderFightMod.LOGGER.warn("Missing End chunk generator settings entry; aborting reseed");
            return null;
        }

        HolderLookup.RegistryLookup<Biome> biomeLookup;
        try {
            biomeLookup = server.registryAccess().lookupOrThrow(Registries.BIOME);
        } catch (RuntimeException ex) {
            EnderFightMod.LOGGER.warn("Failed to resolve biome lookup for End; aborting reseed", ex);
            return null;
        }

        TheEndBiomeSource endBiomeSource = TheEndBiomeSource.create(biomeLookup);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(endBiomeSource, settingsEntry.get());

        HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> noiseParametersLookup;
        try {
            noiseParametersLookup = server.registryAccess().lookupOrThrow(Registries.NOISE);
        } catch (RuntimeException ex) {
            EnderFightMod.LOGGER.warn("Failed to resolve noise parameter lookup for End; terrain may repeat", ex);
            return generator;
        }

        if (((Object) generator) instanceof NoiseChunkGeneratorExtension extension) {
            RandomState customConfig = RandomState.create(
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

    private void registerDimensionOptions(WritableRegistry<LevelStem> registry,
                                          ResourceKey<Level> worldKey,
                                          LevelStem options) {
        ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, worldKey.identifier());
        if (registry.containsKey(dimensionKey)) {
            return;
        }
        if (registry instanceof MappedRegistry<LevelStem> simpleRegistry) {
            @SuppressWarnings("unchecked")
            SimpleRegistryAccessor<LevelStem> accessor = (SimpleRegistryAccessor<LevelStem>) (Object) simpleRegistry;
            boolean wasFrozen = accessor.getFrozen();
            if (wasFrozen) {
                accessor.setFrozen(false);
            }
            try {
                simpleRegistry.register(dimensionKey, options, RegistrationInfo.BUILT_IN);
            } finally {
                if (wasFrozen) {
                    accessor.setFrozen(true);
                }
            }
            return;
        }
        registry.register(dimensionKey, options, RegistrationInfo.BUILT_IN);
    }

    private ServerLevel instantiateEndWorld(MinecraftServer server,
                                            MinecraftServerAccessor accessor,
                                            ResourceKey<Level> worldKey,
                                            LevelStem dimensionOptions,
                                            long newSeed) {
        // CRITICAL: Set seed override BEFORE creating the world, as chunk generation starts immediately
        WorldSeedOverrides.setSeedOverride(worldKey, newSeed);
        EnderFightMod.LOGGER.info("Pre-registered seed override for {}: {}", worldKey.identifier(), newSeed);
        
        WorldData saveProperties = accessor.getSaveProperties();
        ServerLevelData mainWorldProperties = saveProperties.overworldData();
        DerivedLevelData derivedProperties = new DerivedLevelData(saveProperties, mainWorldProperties);
        boolean debugWorld = saveProperties.isDebugWorld();
        long hashedSeed = BiomeManager.obfuscateSeed(newSeed);
        List<CustomSpawner> spawners = ImmutableList.of();

        ServerLevel world = new ServerLevel(
            server,
            accessor.getWorkerExecutor(),
            accessor.getSession(),
            derivedProperties,
            worldKey,
            dimensionOptions,
            debugWorld,
            hashedSeed,
            spawners,
            false
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

    private ResourceKey<Level> createNextDimensionKey(long seed) {
        // Use a stable custom dimension id so old dimensions do not accumulate in the world's dynamic
        // registry (which causes Minecraft to recreate empty folders every boot).
        return STABLE_END_WORLD_KEY;
    }

    private ResourceKey<Level> normalizeActiveEndKey(ResourceKey<Level> key) {
        if (key == null) {
            return STABLE_END_WORLD_KEY;
        }

        Identifier id = key.identifier();
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

        ResourceKey<Level> active = state.getActiveDimensionKey();
        if (active == null || active.identifier() == null) {
            return;
        }

        Identifier id = active.identifier();
        if (!EnderFightMod.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        if (!id.getPath().startsWith("daily_end_")) {
            return;
        }
        if (STABLE_END_ID.equals(id)) {
            return;
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
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

    private WritableRegistry<LevelStem> locateDimensionRegistry(MinecraftServer server) {
        LayeredRegistryAccess<RegistryLayer> combined = server.registries();
        RegistryAccess.Frozen dimensionManager = combined.getLayer(RegistryLayer.DIMENSIONS);
        Optional<Registry<LevelStem>> registry = dimensionManager.lookup(Registries.LEVEL_STEM);
        if (registry.isEmpty()) {
            return null;
        }
        if (registry.get() instanceof WritableRegistry<LevelStem> mutable) {
            return mutable;
        }
        if (registry.get() instanceof MappedRegistry<LevelStem> simple) {
            return simple;
        }
        return null;
    }

    private void cleanupStaleEndDimensions(MinecraftServer server, ResourceKey<Level> activeKeyToKeep) {
        if (server == null) {
            return;
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path baseDir = worldRoot.resolve("dimensions").resolve(EnderFightMod.MOD_ID);
        if (!Files.isDirectory(baseDir)) {
            return;
        }

        Path keepDir = null;
        if (activeKeyToKeep != null) {
            Identifier activeId = activeKeyToKeep.identifier();
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

    protected void deleteWorldDirectory(MinecraftServer server, ResourceKey<Level> worldKey) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path directory = DimensionType.getStorageFolder(worldKey, worldRoot);
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
    private void initializeEnderDragonFight(ServerLevel endWorld) {
        if (endWorld.getDragonFight() == null) {
            EnderFightMod.LOGGER.info("Initializing ender dragon fight for {}", endWorld.dimension().identifier());
            EnderDragonFight dragonFight = EnderDragonFight.createDefault();
            dragonFight.init(endWorld, endWorld.getSeed(), ServerLevel.END_SPAWN_POINT);
            dragonFight.skipArenaLoadedCheck();
            endWorld.setDragonFight(dragonFight);
            dragonFight.tryRespawn();
            EnderFightMod.LOGGER.info("Requested dragon respawn for {}", endWorld.dimension().identifier());
        }
    }

    private boolean hasPendingServerPortalsHandoff(ServerPlayer player) {
        try {
            Class<?> modClass = Class.forName("de.michiruf.serverportals.ServerPortalsMod");
            Method hasPending = modClass.getMethod("hasPendingPortalTeleport", java.util.UUID.class);
            Object result = hasPending.invoke(null, player.getUUID());
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
