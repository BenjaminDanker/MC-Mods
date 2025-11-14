package com.silver.enderfight.reset;

import com.google.common.collect.ImmutableList;
import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates the End reset lifecycle. A tick-based state machine keeps the behaviour deterministic and
 * reusable no matter how often the server restarts.
 */
public class EndResetManager {
    private static final DateTimeFormatter DIMENSION_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final String DRAGON_BOSS_BAR_FIELD_NAME = "field_13119"; // obfuscated getter for EnderDragonFight#bossBar

    private final ConfigManager configManager;

    private EndControlConfig cachedConfig;
    private EndResetPersistentState persistentState;
    private boolean countdownActive;
    private long countdownTicksRemaining;
    private boolean warningSent;
    private RegistryKey<World> activeEndWorldKey = World.END;

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

    public void onServerStarted(MinecraftServer server) {
        this.persistentState = EndResetPersistentState.load(server);
        this.cachedConfig = configManager.getConfig();
        this.activeEndWorldKey = persistentState.getActiveDimensionKey();
        ensureActiveEndWorld(server);
    }

    public void onServerStopped(MinecraftServer server) {
        if (persistentState != null) {
            persistentState.save(server);
        }
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

        if (!countdownActive) {
            double intervalHours = cachedConfig.resetIntervalHours();
            long intervalMillis = (long) Math.max(1D, intervalHours * 3_600_000D);
            long now = Instant.now().toEpochMilli();
            long lastReset = persistentState.getLastResetEpochMillis();
            if (lastReset <= 0 || now - lastReset >= intervalMillis) {
                beginCountdown();
            }
        } else {
            processCountdown(server);
        }
    }

    private void beginCountdown() {
        this.countdownActive = true;
        this.warningSent = false;
        this.countdownTicksRemaining = cachedConfig.warningDelayTicks();
        EnderFightMod.LOGGER.info("End reset countdown started; warning window {} ticks", countdownTicksRemaining);
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
        }
        return success;
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

    EnderFightMod.LOGGER.info("Redirecting player {} from {} into custom End {}", player.getName().getString(), destination.getRegistryKey().getValue(), activeEndWorldKey.getValue());

        ensureEndSpawnPlatform(targetWorld);
        TeleportTarget target = new TeleportTarget(targetWorld, END_PLATFORM_SPAWN, Vec3d.ZERO, END_PLATFORM_YAW, 0.0F, TeleportTarget.NO_OP);
        player.teleportTo(target);
    }

    private void handlePlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        if (persistentState == null) {
            return;
        }

        ServerPlayerEntity player = handler.player;
        RegistryKey<World> recordedKey = persistentState.getRecordedEndDimension(player.getUuid());
        if (recordedKey == null) {
            return;
        }

    RegistryKey<World> activeKey = getActiveEndWorldKey();

        ServerWorld currentWorld = player.getCommandSource().getWorld();
        if (currentWorld != null && !PortalInterceptor.isManagedEndDimension(currentWorld.getRegistryKey())) {
            EnderFightMod.LOGGER.debug("Skipping offline End safeguard for {} – player already in {}",
                player.getName().getString(), currentWorld.getRegistryKey().getValue());
            if (persistentState.clearRecordedPlayer(player.getUuid())) {
                persistentState.save(server);
            }
            return;
        }

        if (!PortalInterceptor.isManagedEndDimension(recordedKey) || recordedKey.equals(activeKey)) {
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

        Text message = Text.literal("The End reset while you were offline; you've been returned to spawn.");
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
    RegistryKey<World> worldKey = player.getCommandSource().getWorld().getRegistryKey();

        tryRemoveFromDragonBossBar(player);

        boolean changed;
        if (PortalInterceptor.isManagedEndDimension(worldKey)) {
            persistentState.recordPlayerLoggedOutInEnd(player.getUuid(), worldKey);
            changed = true;
        } else {
            changed = persistentState.clearRecordedPlayer(player.getUuid());
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

    private void ensureEndSpawnPlatform(ServerWorld world) {
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
        registerDimensionOptions(dimensionRegistry, newWorldKey, newOptions);

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
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
        deleteWorldDirectory(server, oldKey);

        activeEndWorldKey = newWorldKey;
        return true;
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
        String timestamp = DIMENSION_KEY_FORMATTER.format(Instant.now());
        String suffix = Long.toUnsignedString(seed, 16);
    Identifier id = Identifier.of(EnderFightMod.MOD_ID, "daily_end_" + timestamp + "_" + suffix);
        return RegistryKey.of(RegistryKeys.WORLD, id);
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

    protected void deleteWorldDirectory(MinecraftServer server, RegistryKey<World> worldKey) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Path directory = DimensionType.getSaveDirectory(worldKey, worldRoot);
        if (!Files.exists(directory)) {
            EnderFightMod.LOGGER.info("World folder {} missing (already clean)", directory);
            return;
        }

        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            EnderFightMod.LOGGER.info("Deleted End dimension folder at {}", directory);
        } catch (IOException ex) {
            EnderFightMod.LOGGER.error("Failed to delete End dimension directory {}", directory, ex);
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