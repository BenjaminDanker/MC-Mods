package com.silver.atlantis.spawn;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.fluid.Fluids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Spawns mobs based on marker blocks inside the current construct bounds.
 */
public final class SpawnCommandManager {

    private SpawnBatchTask activeTask;
    private int aiThrottleTickCounter;

    private record SpawnedMob(Entity entity, SpawnMobConfig.SpawnType spawnType, int difficulty) {
    }

    private record BuiltCustomization(MobCustomization customization, int positionDifficulty) {
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    public int runStructureMob(ServerCommandSource source, boolean dryRun) {
        return execute(source, dryRun);
    }

    public LiteralArgumentBuilder<ServerCommandSource> buildSubcommand() {
        return CommandManager.literal("structuremob")
            .executes(context -> execute(context.getSource(), false))
            .then(CommandManager.literal("dryrun")
                .executes(context -> execute(context.getSource(), true))
            )
            .then(CommandManager.literal("clear")
                .executes(context -> clearStructureMob(context.getSource()))
            );
    }

    private int clearStructureMob(ServerCommandSource source) {
        MinecraftServer server = source.getServer();

        ActiveConstructBounds activeBounds = ActiveConstructBoundsResolver.tryResolveLatest();
        if (activeBounds == null) {
            source.sendFeedback(() -> Text.literal("No active construct bounds found; nothing to clear."), false);
            return 0;
        }

        ServerWorld world = resolveWorld(server, activeBounds.dimensionId());
        if (world == null) {
            source.sendFeedback(() -> Text.literal("No world loaded for active construct dimension; nothing to clear."), false);
            return 0;
        }

        if (activeTask != null) {
            activeTask = null;
        }

        Box boundsBox = new Box(
            activeBounds.minX(), activeBounds.minY(), activeBounds.minZ(),
            activeBounds.maxX() + 1.0, activeBounds.maxY() + 1.0, activeBounds.maxZ() + 1.0
        );

        String atlantisTag = SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG;
        List<MobEntity> toClear = world.getEntitiesByClass(
            MobEntity.class,
            boundsBox,
            mob -> mob != null && mob.getCommandTags().contains(atlantisTag)
        );

        int persistent = 0;
        for (MobEntity mob : toClear) {
            if (mob == null) {
                continue;
            }
            if (mob.isPersistent()) {
                persistent++;
            }
            mob.discard();
        }

        int cleared = toClear.size();
        int finalPersistent = persistent;
        source.sendFeedback(() -> Text.literal(String.format(
            Locale.ROOT,
            "Cleared %d Atlantis structuremob mob(s) (persistent=%d) within active construct bounds.",
            cleared,
            finalPersistent
        )), false);

        if (SpawnMobConfig.DIAGNOSTIC_LOGS) {
            AtlantisMod.LOGGER.info(
                "[SpawnDiag] clear dim={} cleared={} persistentCleared={} bounds=({},{},{})..({},{},{})",
                world.getRegistryKey().getValue(),
                cleared,
                finalPersistent,
                activeBounds.minX(), activeBounds.minY(), activeBounds.minZ(),
                activeBounds.maxX(), activeBounds.maxY(), activeBounds.maxZ()
            );
        }

        return cleared;
    }

    private int execute(ServerCommandSource source, boolean dryRun) {
        if (!dryRun && activeTask != null) {
            source.sendFeedback(() -> Text.literal("A /structuremob spawn job is already running."), false);
            return 0;
        }

        MinecraftServer server = source.getServer();

        ActiveConstructBounds activeBounds = ActiveConstructBoundsResolver.tryResolveLatest();
        if (activeBounds == null) {
            source.sendFeedback(() -> Text.literal("No active construct bounds found; /structuremob only spawns within the current cycle build. Run /construct first."), false);
            return 0;
        }

        ServerWorld world = resolveWorld(server, activeBounds.dimensionId());
        if (world == null) {
            source.sendFeedback(() -> Text.literal("No world loaded for active construct dimension."), false);
            return 0;
        }

        List<SpawnMarker> markers = findSpawnMarkers(world, activeBounds);
        if (markers.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No spawn markers found in the active construct bounds."), false);
            return 0;
        }

        Random random = new Random(world.getRandom().nextLong());
        List<SpawnMarker> selectedMarkers = selectMarkers(markers, activeBounds, random);

        if (!dryRun) {
            activeTask = new SpawnBatchTask(source, world, activeBounds, selectedMarkers, random.nextLong());
            source.sendFeedback(() -> Text.literal(String.format(
                Locale.ROOT,
                "Started gradual /structuremob: %d marker(s), maxAttemptsPerTick=%d, tickBudgetMs=%.2f",
                selectedMarkers.size(),
                Math.max(1, SpawnMobConfig.MAX_SPAWN_ATTEMPTS_PER_TICK),
                Math.max(250_000L, SpawnMobConfig.SPAWN_TICK_BUDGET_NANOS) / 1_000_000.0
            )), false);
            return 1;
        }

        List<Entity> spawnedEntities = new ArrayList<>();
        List<SpawnedMob> spawnedMobs = new ArrayList<>();
        int eligible = 0;
        int spawned = 0;
        int spawnedLand = 0;
        int spawnedWater = 0;
        int spawnedAir = 0;
        int spawnedBoss = 0;
        int specialTotal = 0;
        int specialTaggedEntities = 0;

        for (SpawnMarker marker : selectedMarkers) {
            Optional<BuiltCustomization> builtOpt = buildCustomization(marker, activeBounds, random);
            if (builtOpt.isEmpty()) {
                continue;
            }

            BuiltCustomization built = builtOpt.get();

            eligible++;
            if (dryRun) {
                spawned++;
                continue;
            }

            Optional<Entity> entityOpt = MobSpawner.spawn(world, built.customization());
            if (entityOpt.isPresent()) {
                spawned++;
                Entity entity = entityOpt.get();
                spawnedEntities.add(entity);

                int maxDifficulty = SpawnMobConfig.maxDifficultyFor(marker.spawnType());
                int difficulty = Math.max(1, Math.min(built.positionDifficulty(), maxDifficulty));
                spawnedMobs.add(new SpawnedMob(entity, marker.spawnType(), difficulty));

                if (marker.spawnType() == SpawnMobConfig.SpawnType.LAND) {
                    spawnedLand++;
                } else if (marker.spawnType() == SpawnMobConfig.SpawnType.WATER) {
                    spawnedWater++;
                } else if (marker.spawnType() == SpawnMobConfig.SpawnType.AIR) {
                    spawnedAir++;
                } else {
                    spawnedBoss++;
                }

                int specialAmount = rollSpecialDropAmount(new SpawnedMob(entity, marker.spawnType(), difficulty), random);
                if (specialAmount > 0) {
                    SpecialDropManager.markSpecialDropAmount(entity, specialAmount);
                    specialTotal += specialAmount;
                    specialTaggedEntities++;
                }
            }
        }

        String summary = String.format(Locale.ROOT,
            "%s %d mob(s) from %d marker(s) (land=%d water=%d air=%d boss=%d specialItems=%d specialTagged=%d) within active construct.",
            dryRun ? "Would spawn" : "Spawned",
            spawned,
            eligible,
            spawnedLand,
            spawnedWater,
            spawnedAir,
            spawnedBoss,
            specialTotal,
            specialTaggedEntities
        );
        source.sendFeedback(() -> Text.literal(summary), false);

        logSpawnDiagnostics(world, activeBounds, spawnedMobs);

        return spawned;
    }

    private void onEndTick(MinecraftServer server) {
        if (activeTask == null) {
            throttleAtlantisMobAiBySimulationDistance(server);
            return;
        }

        boolean done;
        try {
            done = activeTask.tick(server);
        } catch (Throwable t) {
            AtlantisMod.LOGGER.error("Gradual structuremob spawn crashed; cancelling job.", t);
            done = true;
        }

        if (done) {
            activeTask = null;
        }

        throttleAtlantisMobAiBySimulationDistance(server);
    }

    private void throttleAtlantisMobAiBySimulationDistance(MinecraftServer server) {
        if (server == null) {
            return;
        }

        aiThrottleTickCounter++;
        if ((aiThrottleTickCounter % 10) != 0) {
            return;
        }

        int simulationDistanceChunks = Math.max(0, server.getPlayerManager().getSimulationDistance());
        String atlantisTag = SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG;

        for (ServerWorld world : server.getWorlds()) {
            List<ServerPlayerEntity> players = world.getPlayers();
            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof MobEntity mob)) {
                    continue;
                }
                if (!mob.getCommandTags().contains(atlantisTag)) {
                    continue;
                }

                boolean shouldTickAi = isWithinSimulationDistanceOfAnyPlayer(mob, players, simulationDistanceChunks);
                boolean shouldDisableAi = !shouldTickAi;
                if (mob.isAiDisabled() != shouldDisableAi) {
                    mob.setAiDisabled(shouldDisableAi);
                    if (shouldDisableAi) {
                        mob.setTarget(null);
                    }
                }
            }
        }
    }

    private boolean isWithinSimulationDistanceOfAnyPlayer(MobEntity mob, List<ServerPlayerEntity> players, int simulationDistanceChunks) {
        if (mob == null || players == null || players.isEmpty()) {
            return false;
        }

        int mobChunkX = mob.getBlockX() >> 4;
        int mobChunkZ = mob.getBlockZ() >> 4;

        for (ServerPlayerEntity player : players) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }

            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            int dx = Math.abs(playerChunkX - mobChunkX);
            int dz = Math.abs(playerChunkZ - mobChunkZ);
            if (Math.max(dx, dz) <= simulationDistanceChunks) {
                return true;
            }
        }

        return false;
    }

    private void logSpawnDiagnostics(ServerWorld world, ActiveConstructBounds bounds, List<SpawnedMob> spawnedMobs) {
        if (!SpawnMobConfig.DIAGNOSTIC_LOGS || world == null || bounds == null) {
            return;
        }

        int spawnedCount = spawnedMobs == null ? 0 : spawnedMobs.size();
        int spawnedUniqueChunks = 0;
        if (spawnedMobs != null && !spawnedMobs.isEmpty()) {
            java.util.Set<Long> chunkKeys = new java.util.HashSet<>();
            for (SpawnedMob spawnedMob : spawnedMobs) {
                if (spawnedMob == null || spawnedMob.entity() == null) {
                    continue;
                }
                BlockPos pos = spawnedMob.entity().getBlockPos();
                chunkKeys.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            }
            spawnedUniqueChunks = chunkKeys.size();
        }

        Box boundsBox = new Box(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0
        );
        List<MobEntity> mobsInBounds = world.getEntitiesByClass(MobEntity.class, boundsBox, m -> true);
        int persistentInBounds = 0;
        for (MobEntity mob : mobsInBounds) {
            if (mob != null && mob.isPersistent()) {
                persistentInBounds++;
            }
        }

        AtlantisMod.LOGGER.info(
            "[SpawnDiag] dim={} spawnedThisRun={} spawnedUniqueChunks={} mobsInBounds={} persistentMobsInBounds={} bounds=({},{},{})..({},{},{})",
            world.getRegistryKey().getValue(),
            spawnedCount,
            spawnedUniqueChunks,
            mobsInBounds.size(),
            persistentInBounds,
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );
    }

    private int rollSpecialDropAmount(SpawnedMob mob, Random random) {
        if (mob == null || mob.entity() == null) {
            return 0;
        }

        if (random == null) {
            random = new Random();
        }

        SpawnMobConfig.SpawnType spawnType = mob.spawnType();
        int difficulty = Math.max(1, mob.difficulty());
        int maxDifficulty = SpawnMobConfig.maxDifficultyFor(spawnType);
        double normalizedDifficulty = Math.max(0.0, Math.min(1.0, difficulty / (double) Math.max(1, maxDifficulty)));

        if (spawnType == SpawnMobConfig.SpawnType.BOSS) {
            int minAmount = Math.max(1, SpawnSpecialConfig.BOSS_SPECIAL_DROP_MIN);
            int maxAmount = Math.max(minAmount, Math.max(1, SpawnSpecialConfig.SPECIAL_DROP_MAX_AMOUNT_BOSS));
            int amount = (int) Math.round(minAmount + (normalizedDifficulty * (maxAmount - minAmount)));
            amount = Math.max(minAmount, Math.min(maxAmount, amount));

            if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS) {
                AtlantisMod.LOGGER.info(
                    "[SpecialDropRoll] type={} difficulty={} normalized={} tierAmount={}",
                    spawnType,
                    difficulty,
                    normalizedDifficulty,
                    amount
                );
            }
            return amount;
        }

        // Non-boss: tiered scaling based on POSITION difficulty.
        // Keep low-difficulty chance similar to previous behavior, but scale AMOUNT smoothly
        // so higher position difficulty yields much larger drops.
        int maxAmount = Math.max(1, SpawnSpecialConfig.SPECIAL_DROP_MAX_AMOUNT_NON_BOSS);
        double variance = Math.max(0.0, SpawnSpecialConfig.SPECIAL_DROP_RANDOMNESS_PERCENT) / 100.0;

        double chance;
        if (difficulty < 10) {
            double progress = (difficulty - 1) / 9.0;
            chance = 0.15 + (0.65 * Math.max(0.0, Math.min(1.0, progress)));
            if (random.nextDouble() >= chance) {
                if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS) {
                    AtlantisMod.LOGGER.info(
                        "[SpecialDropRoll] type={} difficulty={} normalized={} chance={} rolledAmount=0 final=0",
                        spawnType,
                        difficulty,
                        normalizedDifficulty,
                        chance
                    );
                }
                return 0;
            }
        } else {
            chance = 1.0;
        }

        // Amount curve: exponent slightly > 1 makes the high end ramp harder without
        // making mid difficulty too stingy.
        double curve = Math.pow(normalizedDifficulty, 1.3);
        double amountF = 1.0 + (curve * (maxAmount - 1));
        if (variance > 0.0) {
            double jitter = (random.nextDouble() * 2.0) - 1.0; // -1..+1
            amountF = amountF * (1.0 + (variance * jitter));
        }

        int amount = (int) Math.round(amountF);
        amount = Math.max(1, Math.min(maxAmount, amount));

        if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS) {
            AtlantisMod.LOGGER.info(
                "[SpecialDropRoll] type={} difficulty={} normalized={} chance={} rolledAmount={} final={} ",
                spawnType,
                difficulty,
                normalizedDifficulty,
                chance,
                amount,
                amount
            );
        }

        return Math.max(0, amount);
    }

    private ServerWorld resolveWorld(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return server.getWorld(World.OVERWORLD);
        }

        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(dimensionId)) {
                return world;
            }
        }
        return null;
    }

    private List<SpawnMarker> findSpawnMarkers(ServerWorld world, ActiveConstructBounds bounds) {
        List<SpawnMarker> markers = new ArrayList<>();

        int landMarkers = 0;
        int waterMarkers = 0;
        int bossMarkers = 0;
        int airMarkers = 0;
        AirSpawnStats airStats = new AirSpawnStats();

        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk;
                try {
                    chunk = world.getChunk(chunkX, chunkZ);
                } catch (Exception e) {
                    continue;
                }

                ChunkPos chunkPos = chunk.getPos();
                int startX = Math.max(bounds.minX(), chunkPos.getStartX());
                int endX = Math.min(bounds.maxX(), chunkPos.getEndX());
                int startZ = Math.max(bounds.minZ(), chunkPos.getStartZ());
                int endZ = Math.min(bounds.maxZ(), chunkPos.getEndZ());
                int easyY = resolveEasyY(bounds);

                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int x = startX; x <= endX; x++) {
                        for (int z = startZ; z <= endZ; z++) {
                            mutable.set(x, y, z);
                            BlockState state = chunk.getBlockState(mutable);

                            SpawnMobConfig.SpawnType spawnType = spawnTypeFor(state);
                            if (spawnType == null) {
                                continue;
                            }

                            BlockPos abovePos = new BlockPos(x, y + 1, z);
                            BlockPos aboveTwoPos = abovePos.up();
                            BlockState aboveState = world.getBlockState(abovePos);
                            BlockState aboveTwoState = world.getBlockState(aboveTwoPos);

                            boolean hasSpace;
                            if (spawnType == SpawnMobConfig.SpawnType.WATER) {
                                hasSpace = aboveState.getFluidState().isOf(Fluids.WATER);
                            } else if (spawnType == SpawnMobConfig.SpawnType.LAND) {
                                hasSpace = (aboveState.isAir() || aboveState.getFluidState().isOf(Fluids.WATER))
                                    && (aboveTwoState.isAir() || aboveTwoState.getFluidState().isOf(Fluids.WATER));
                            } else {
                                hasSpace = (aboveState.isAir() || aboveState.getFluidState().isOf(Fluids.WATER))
                                    && (aboveTwoState.isAir() || aboveTwoState.getFluidState().isOf(Fluids.WATER));
                            }

                            if (!hasSpace) {
                                continue;
                            }

                            markers.add(new SpawnMarker(abovePos.toImmutable(), spawnType));
                            if (spawnType == SpawnMobConfig.SpawnType.LAND) {
                                landMarkers++;
                            } else if (spawnType == SpawnMobConfig.SpawnType.WATER) {
                                waterMarkers++;
                            } else if (spawnType == SpawnMobConfig.SpawnType.BOSS) {
                                bossMarkers++;
                            }

                            if (spawnType == SpawnMobConfig.SpawnType.LAND && abovePos.getY() > easyY) {
                                Optional<BlockPos> airSpawnPos = findAirSpawnPosition(world, abovePos, bounds, airStats);
                                if (airSpawnPos.isPresent()) {
                                    markers.add(new SpawnMarker(airSpawnPos.get(), SpawnMobConfig.SpawnType.AIR));
                                    airMarkers++;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (SpawnMobConfig.DIAGNOSTIC_LOGS) {
            AtlantisMod.LOGGER.info(
                "[SpawnDiag] markers land={} water={} boss={} air={} airAttempts={} airNoGlass={} airTooShort={} airNotFound={} minAirStretch={} maxAirPerRun={}",
                landMarkers,
                waterMarkers,
                bossMarkers,
                airMarkers,
                airStats.attempts,
                airStats.noGlass,
                airStats.tooShort,
                airStats.notFound,
                Math.max(2, SpawnMobConfig.MIN_AIR_STRETCH_TO_GLASS_BLOCKS),
                SpawnMobConfig.MAX_AIR_PER_RUN
            );
        }

        return markers;
    }

    private SpawnMobConfig.SpawnType spawnTypeFor(BlockState state) {
        if (state.isOf(Blocks.SANDSTONE)) {
            return SpawnMobConfig.SpawnType.LAND;
        }
        if (state.isOf(Blocks.DIAMOND_BLOCK)) {
            return SpawnMobConfig.SpawnType.WATER;
        }
        if (state.isOf(Blocks.WHITE_STAINED_GLASS)) {
            return SpawnMobConfig.SpawnType.BOSS;
        }
        return null;
    }

    private Optional<BuiltCustomization> buildCustomization(SpawnMarker marker, ActiveConstructBounds bounds, Random random) {
        double normalizedDifficulty = computeNormalizedDifficulty(marker.pos(), bounds);

        int maxDifficulty = SpawnMobConfig.maxDifficultyFor(marker.spawnType());
        int positionDifficulty = Math.max(1, (int) Math.round(normalizedDifficulty * maxDifficulty));

        SpawnMobConfig.MobLoadout loadout = SpawnMobConfig.buildLoadout(marker.spawnType(), positionDifficulty, random, false);
        if (loadout == null) {
            return Optional.empty();
        }

        MobCustomization.Builder builder = MobCustomization.builder(loadout.entityId());
        builder.position(marker.pos().getX() + 0.5, marker.pos().getY(), marker.pos().getZ() + 0.5);
        builder.rotation(random.nextFloat() * 360f, 0f);

        if (loadout.health() > 0) {
            builder.health(loadout.health());
            builder.maxHealth(loadout.health());
        }

        builder.creeperPowered(loadout.creeperPowered());
        builder.creeperExplosionRadius(loadout.creeperExplosionRadius());

        for (SpawnMobConfig.EquipmentConfig equip : loadout.equipment()) {
            ItemStack stack = createItemStack(equip);
            if (stack.isEmpty()) {
                continue;
            }

            List<MobCustomization.EnchantmentEntry> enchantments = new ArrayList<>();
            for (SpawnMobConfig.EnchantmentConfig ench : equip.enchantments()) {
                enchantments.add(new MobCustomization.EnchantmentEntry(ench.enchantmentId(), ench.level()));
            }

            builder.equipment(equip.slot(), stack, enchantments);
        }

        for (SpawnMobConfig.EffectConfig effect : loadout.effects()) {
            builder.effect(
                effect.effectId(),
                SpawnMobConfig.YEAR_TICKS,
                effect.amplifier(),
                effect.ambient(),
                effect.showParticles()
            );
        }

        return Optional.of(new BuiltCustomization(builder.build(), positionDifficulty));
    }

    private ItemStack createItemStack(SpawnMobConfig.EquipmentConfig equip) {
        Identifier id = Identifier.tryParse(equip.itemId());
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid equipment item id: {}", equip.itemId());
            return ItemStack.EMPTY;
        }

        Item item = Registries.ITEM.get(id);
        if (item == null || item == net.minecraft.item.Items.AIR) {
            AtlantisMod.LOGGER.warn("Unknown equipment item: {}", equip.itemId());
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private double computeNormalizedDifficulty(BlockPos spawnPos, ActiveConstructBounds bounds) {
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0;

        double rx = Math.max(1.0, (bounds.maxX() - bounds.minX()) / 2.0);
        double rz = Math.max(1.0, (bounds.maxZ() - bounds.minZ()) / 2.0);
        double dx = (spawnPos.getX() + 0.5) - centerX;
        double dz = (spawnPos.getZ() + 0.5) - centerZ;
        double normalized = Math.sqrt((dx * dx) / (rx * rx) + (dz * dz) / (rz * rz));
        double centerFactor = 1.0 - clamp01(normalized);
        centerFactor = clamp01(centerFactor);

        int easyY = resolveEasyY(bounds);
        double heightFactor;
        double depthFactor;

        if (spawnPos.getY() >= easyY) {
            int heightRange = Math.max(1, bounds.maxY() - easyY);
            heightFactor = (spawnPos.getY() - easyY) / (double) heightRange;
            depthFactor = 0.0;
        } else {
            int depthRange = Math.max(1, easyY - bounds.minY());
            depthFactor = (easyY - spawnPos.getY()) / (double) depthRange;
            heightFactor = 0.0;
        }

        heightFactor = clamp01(heightFactor);
        depthFactor = clamp01(depthFactor);

        double weighted = centerFactor * SpawnDifficultyConfig.CENTER_DIFFICULTY_WEIGHT
            + heightFactor * SpawnDifficultyConfig.HEIGHT_DIFFICULTY_WEIGHT
            + depthFactor * SpawnDifficultyConfig.DEEP_DIFFICULTY_WEIGHT;

        double totalWeight = SpawnDifficultyConfig.CENTER_DIFFICULTY_WEIGHT
            + SpawnDifficultyConfig.HEIGHT_DIFFICULTY_WEIGHT
            + SpawnDifficultyConfig.DEEP_DIFFICULTY_WEIGHT;

        if (totalWeight <= 0) {
            return 0.0;
        }

        return clamp01(weighted / totalWeight);
    }

    private List<SpawnMarker> selectMarkers(List<SpawnMarker> markers, ActiveConstructBounds bounds, Random random) {
        int maxNonBoss = SpawnMobConfig.MAX_NON_BOSS_PER_RUN;
        int maxAir = SpawnMobConfig.MAX_AIR_PER_RUN;
        int minSeparation = SpawnMobConfig.MIN_SPAWN_SEPARATION_BLOCKS;

        List<SpawnMarker> bossMarkers = new ArrayList<>();
        List<SpawnMarker> airMarkers = new ArrayList<>();
        List<SpawnMarker> nonBossMarkers = new ArrayList<>();
        for (SpawnMarker marker : markers) {
            if (marker.spawnType() == SpawnMobConfig.SpawnType.BOSS) {
                bossMarkers.add(marker);
            } else if (marker.spawnType() == SpawnMobConfig.SpawnType.AIR) {
                airMarkers.add(marker);
            } else {
                nonBossMarkers.add(marker);
            }
        }

        List<SpawnMarker> selected = new ArrayList<>(markers.size());
        selected.addAll(bossMarkers);

        if (nonBossMarkers.isEmpty()) {
            return selected;
        }

        // Prefer higher position difficulty markers so harder areas get higher mob density.
        // We do this via coarse difficulty buckets to avoid an O(n log n) sort when marker counts are large.
        nonBossMarkers = bucketShuffleByDifficulty(nonBossMarkers, bounds, random);
        airMarkers = bucketShuffleByDifficulty(airMarkers, bounds, random);

        int limit = maxNonBoss <= 0 ? Integer.MAX_VALUE : maxNonBoss;
        int airLimit = maxAir <= 0 ? 0 : maxAir;
        if (minSeparation <= 0 && limit == Integer.MAX_VALUE && airLimit == Integer.MAX_VALUE) {
            selected.addAll(nonBossMarkers);
            selected.addAll(airMarkers);
            return selected;
        }

        int cellSize = Math.max(1, minSeparation);
        Map<Long, List<BlockPos>> cellMap = new HashMap<>();
        List<BlockPos> bossPositions = new ArrayList<>();
        if (minSeparation > 0) {
            for (SpawnMarker boss : bossMarkers) {
                addToCellMap(cellMap, boss.pos(), cellSize);
                bossPositions.add(boss.pos());
            }
        }

        int added = 0;
        if (minSeparation > 0 && limit != Integer.MAX_VALUE) {
            // Chunk-distributed selection: prevents the cap from over-filling a small area and
            // leaving large regions empty. Still biased toward harder (center/high/low) chunks.
            added = addChunkDistributed(nonBossMarkers, bounds, random, selected, cellMap, cellSize, minSeparation, limit);
        }

        // Fallback: fill remaining picks (or handle cases with separation disabled).
        for (SpawnMarker marker : nonBossMarkers) {
            if (added >= limit) {
                break;
            }

            if (minSeparation > 0) {
                int effectiveSeparation = effectiveMinSeparation(marker.pos(), bounds, minSeparation);
                if (!isFarEnough(marker.pos(), cellMap, cellSize, effectiveSeparation, bossPositions, minSeparation)) {
                    continue;
                }
            }

            selected.add(marker);
            added++;
            if (minSeparation > 0) {
                addToCellMap(cellMap, marker.pos(), cellSize);
            }
        }

        int addedAir = 0;
        for (SpawnMarker marker : airMarkers) {
            if (addedAir >= airLimit) {
                break;
            }

            selected.add(marker);
            addedAir++;
        }

        return selected;
    }

    private int addChunkDistributed(
        List<SpawnMarker> markers,
        ActiveConstructBounds bounds,
        Random random,
        List<SpawnMarker> out,
        Map<Long, List<BlockPos>> cellMap,
        int cellSize,
        int baseMinSeparation,
        int limit
    ) {
        if (markers == null || markers.isEmpty() || bounds == null || random == null || out == null) {
            return 0;
        }

        Map<Long, List<SpawnMarker>> byChunk = new HashMap<>();
        Map<Long, Double> chunkScore = new HashMap<>();
        for (SpawnMarker marker : markers) {
            if (marker == null) {
                continue;
            }
            BlockPos pos = marker.pos();
            long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
            byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(marker);
            double score = clamp01(computeNormalizedDifficulty(pos, bounds));
            Double existing = chunkScore.get(key);
            if (existing == null || score > existing) {
                chunkScore.put(key, score);
            }
        }

        // Shuffle each chunk's candidate list so repeated passes don't lock into a pattern.
        for (List<SpawnMarker> list : byChunk.values()) {
            Collections.shuffle(list, random);
        }

        // Weighted cycle: harder chunks appear more frequently.
        List<Long> cycle = new ArrayList<>();
        for (Long key : byChunk.keySet()) {
            double score = chunkScore.getOrDefault(key, 0.0);
            int weight = 1 + (int) Math.floor(score * 3.0); // 1..4
            for (int i = 0; i < weight; i++) {
                cycle.add(key);
            }
        }
        cycle.sort((a, b) -> Double.compare(chunkScore.getOrDefault(b, 0.0), chunkScore.getOrDefault(a, 0.0)));

        int added = 0;
        int idx = 0;
        int safety = 0;
        while (added < limit && !cycle.isEmpty() && safety < (limit * 50)) {
            safety++;
            if (idx >= cycle.size()) {
                idx = 0;
            }

            long key = cycle.get(idx++);
            List<SpawnMarker> list = byChunk.get(key);
            if (list == null || list.isEmpty()) {
                removeAllOccurrences(cycle, key);
                byChunk.remove(key);
                continue;
            }

            SpawnMarker marker = list.remove(list.size() - 1);

            int effectiveSeparation = effectiveMinSeparation(marker.pos(), bounds, baseMinSeparation);
            if (!isFarEnough(marker.pos(), cellMap, cellSize, effectiveSeparation, List.of(), baseMinSeparation)) {
                continue;
            }

            out.add(marker);
            added++;
            addToCellMap(cellMap, marker.pos(), cellSize);
        }

        return added;
    }

    private int effectiveMinSeparation(BlockPos pos, ActiveConstructBounds bounds, int baseMinSeparation) {
        if (baseMinSeparation <= 0 || pos == null || bounds == null) {
            return baseMinSeparation;
        }

        // Non-linear density scaling:
        // - Low difficulty stays ~unchanged.
        // - High difficulty can pack closer by reducing the effective separation.
        double score = clamp01(computeNormalizedDifficulty(pos, bounds));
        double maxReduction = 0.50; // up to 50% closer at the very hardest spots
        double gamma = 2.5;         // non-linear: keeps low difficulty effectively unchanged

        double reduction = maxReduction * Math.pow(score, gamma);
        double multiplier = 1.0 - reduction;

        int effective = (int) Math.round(baseMinSeparation * multiplier);
        effective = Math.max(2, Math.min(baseMinSeparation, effective));
        return effective;
    }

    private void removeAllOccurrences(List<Long> list, long value) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i) == value) {
                list.remove(i);
            }
        }
    }

    private List<SpawnMarker> bucketShuffleByDifficulty(List<SpawnMarker> markers, ActiveConstructBounds bounds, Random random) {
        if (markers == null || markers.isEmpty() || bounds == null) {
            return markers == null ? List.of() : markers;
        }

        int bucketCount = 16;
        List<List<SpawnMarker>> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new ArrayList<>());
        }

        for (SpawnMarker marker : markers) {
            if (marker == null) {
                continue;
            }
            double score = clamp01(computeNormalizedDifficulty(marker.pos(), bounds));
            int idx = (int) Math.floor(score * bucketCount);
            if (idx >= bucketCount) {
                idx = bucketCount - 1;
            }
            if (idx < 0) {
                idx = 0;
            }
            buckets.get(idx).add(marker);
        }

        List<SpawnMarker> ordered = new ArrayList<>(markers.size());
        for (int i = bucketCount - 1; i >= 0; i--) {
            List<SpawnMarker> bucket = buckets.get(i);
            if (bucket.isEmpty()) {
                continue;
            }
            Collections.shuffle(bucket, random);
            ordered.addAll(bucket);
        }

        return ordered;
    }

    private Optional<BlockPos> findAirSpawnPosition(ServerWorld world, BlockPos spawnPos, ActiveConstructBounds bounds, AirSpawnStats stats) {
        if (world == null || spawnPos == null || bounds == null) {
            return Optional.empty();
        }

        if (stats != null) {
            stats.attempts++;
        }

        int x = spawnPos.getX();
        int z = spawnPos.getZ();
        int startY = Math.max(spawnPos.getY(), bounds.minY());
        int topY = bounds.maxY();
        int minStretch = Math.max(2, SpawnMobConfig.MIN_AIR_STRETCH_TO_GLASS_BLOCKS);

        int currentAirStart = Integer.MIN_VALUE;
        for (int y = startY; y <= topY; y++) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));

            if (state.isAir()) {
                if (currentAirStart == Integer.MIN_VALUE) {
                    currentAirStart = y;
                }
                continue;
            }

            if (state.isOf(Blocks.GLASS)) {
                if (currentAirStart == Integer.MIN_VALUE) {
                    if (stats != null) {
                        stats.noGlass++;
                    }
                    return Optional.empty();
                }

                int airLength = y - currentAirStart;
                if (airLength < minStretch) {
                    if (stats != null) {
                        stats.tooShort++;
                    }
                    return Optional.empty();
                }

                int spawnY = currentAirStart + (airLength / 2);
                if (stats != null) {
                    stats.found++;
                }
                return Optional.of(new BlockPos(x, spawnY, z));
            }

            currentAirStart = Integer.MIN_VALUE;
        }

        if (stats != null) {
            stats.notFound++;
        }

        return Optional.empty();
    }

    private static final class AirSpawnStats {
        private int attempts;
        private int found;
        private int noGlass;
        private int tooShort;
        private int notFound;
    }

    private final class SpawnBatchTask {
        private final ServerCommandSource source;
        private final ServerWorld world;
        private final ActiveConstructBounds bounds;
        private final List<SpawnMarker> markers;
        private final Random random;

        private int index;
        private int eligible;
        private int spawned;
        private int spawnedLand;
        private int spawnedWater;
        private int spawnedAir;
        private int spawnedBoss;
        private int specialTotal;
        private int specialTaggedEntities;
        private final List<SpawnedMob> spawnedMobs = new ArrayList<>();

        private SpawnBatchTask(
            ServerCommandSource source,
            ServerWorld world,
            ActiveConstructBounds bounds,
            List<SpawnMarker> markers,
            long seed
        ) {
            this.source = source;
            this.world = world;
            this.bounds = bounds;
            this.markers = markers == null ? List.of() : markers;
            this.random = new Random(seed);
        }

        private boolean tick(MinecraftServer server) {
            if (markers.isEmpty()) {
                source.sendFeedback(() -> Text.literal("/structuremob finished: no eligible markers."), false);
                return true;
            }

            ServerWorld liveWorld = server.getWorld(world.getRegistryKey());
            if (liveWorld == null) {
                source.sendFeedback(() -> Text.literal("/structuremob cancelled: target world is not loaded."), false);
                return true;
            }

            long deadline = System.nanoTime() + Math.max(250_000L, SpawnMobConfig.SPAWN_TICK_BUDGET_NANOS);
            int attemptsThisTick = 0;
            int maxAttemptsThisTick = Math.max(1, SpawnMobConfig.MAX_SPAWN_ATTEMPTS_PER_TICK);

            while (index < markers.size() && attemptsThisTick < maxAttemptsThisTick && System.nanoTime() < deadline) {
                SpawnMarker marker = markers.get(index++);
                attemptsThisTick++;

                Optional<BuiltCustomization> builtOpt = buildCustomization(marker, bounds, random);
                if (builtOpt.isEmpty()) {
                    continue;
                }

                eligible++;

                Optional<Entity> entityOpt = MobSpawner.spawn(world, builtOpt.get().customization());
                if (entityOpt.isEmpty()) {
                    continue;
                }

                spawned++;
                int maxDifficulty = SpawnMobConfig.maxDifficultyFor(marker.spawnType());
                int difficulty = Math.max(1, Math.min(builtOpt.get().positionDifficulty(), maxDifficulty));
                spawnedMobs.add(new SpawnedMob(entityOpt.get(), marker.spawnType(), difficulty));

                if (marker.spawnType() == SpawnMobConfig.SpawnType.LAND) {
                    spawnedLand++;
                } else if (marker.spawnType() == SpawnMobConfig.SpawnType.WATER) {
                    spawnedWater++;
                } else if (marker.spawnType() == SpawnMobConfig.SpawnType.AIR) {
                    spawnedAir++;
                } else {
                    spawnedBoss++;
                }

                int specialAmount = rollSpecialDropAmount(new SpawnedMob(entityOpt.get(), marker.spawnType(), difficulty), random);
                if (specialAmount > 0) {
                    SpecialDropManager.markSpecialDropAmount(entityOpt.get(), specialAmount);
                    specialTotal += specialAmount;
                    specialTaggedEntities++;
                }
            }

            if (index < markers.size()) {
                return false;
            }

            int finalSpecialTotal = specialTotal;
            int finalSpecialTaggedEntities = specialTaggedEntities;
            String summary = String.format(Locale.ROOT,
                "Spawned %d mob(s) from %d marker(s) (land=%d water=%d air=%d boss=%d specialItems=%d specialTagged=%d) within active construct.",
                spawned,
                eligible,
                spawnedLand,
                spawnedWater,
                spawnedAir,
                spawnedBoss,
                finalSpecialTotal,
                finalSpecialTaggedEntities
            );
            source.sendFeedback(() -> Text.literal(summary), false);
            logSpawnDiagnostics(world, bounds, spawnedMobs);
            return true;
        }
    }

    private boolean isFarEnough(
        BlockPos pos,
        Map<Long, List<BlockPos>> cellMap,
        int cellSize,
        int minSeparation,
        List<BlockPos> bossPositions,
        int bossMinSeparation
    ) {
        int cellX = Math.floorDiv(pos.getX(), cellSize);
        int cellZ = Math.floorDiv(pos.getZ(), cellSize);
        int minSepSq = minSeparation * minSeparation;

        if (bossMinSeparation > 0 && bossPositions != null && !bossPositions.isEmpty()) {
            int bossSepSq = bossMinSeparation * bossMinSeparation;
            for (BlockPos other : bossPositions) {
                int ox = other.getX() - pos.getX();
                int oz = other.getZ() - pos.getZ();
                if (ox * ox + oz * oz < bossSepSq) {
                    return false;
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = cellKey(cellX + dx, cellZ + dz);
                List<BlockPos> existing = cellMap.get(key);
                if (existing == null) {
                    continue;
                }
                for (BlockPos other : existing) {
                    int ox = other.getX() - pos.getX();
                    int oz = other.getZ() - pos.getZ();
                    if (ox * ox + oz * oz < minSepSq) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void addToCellMap(Map<Long, List<BlockPos>> cellMap, BlockPos pos, int cellSize) {
        int cellX = Math.floorDiv(pos.getX(), cellSize);
        int cellZ = Math.floorDiv(pos.getZ(), cellSize);
        long key = cellKey(cellX, cellZ);
        cellMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos);
    }

    private long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }

    private int resolveEasyY(ActiveConstructBounds bounds) {
        int relativeEasy = SpawnDifficultyConfig.SCHEMATIC_EASIEST_Y_REL;
        int relativeMin = SpawnDifficultyConfig.SCHEMATIC_MIN_Y_REL;
        int relativeMax = SpawnDifficultyConfig.SCHEMATIC_MAX_Y_REL;

        int relativeClamped = Math.min(relativeMax, Math.max(relativeMin, relativeEasy));
        int offset = relativeClamped - relativeMin;
        int easyY = bounds.minY() + offset;
        if (easyY < bounds.minY()) {
            return bounds.minY();
        }
        if (easyY > bounds.maxY()) {
            return bounds.maxY();
        }
        return easyY;
    }

    private double clamp01(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }

    private record SpawnMarker(BlockPos pos, SpawnMobConfig.SpawnType spawnType) {
    }
}
