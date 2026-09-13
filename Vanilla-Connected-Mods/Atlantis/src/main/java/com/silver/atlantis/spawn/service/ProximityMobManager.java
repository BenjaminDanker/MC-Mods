package com.silver.atlantis.spawn.service;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.cycle.CycleJsonIO;
import com.silver.atlantis.cycle.CyclePaths;
import com.silver.atlantis.cycle.CycleState;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.drop.SpawnSpecialConfig;
import com.silver.atlantis.spawn.marker.AtlantisMobMarker;
import com.silver.atlantis.spawn.marker.AtlantisMobMarkerState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Proximity-driven runtime for Atlantis dungeon mobs.
 */
public final class ProximityMobManager {

    /**
     * Proximity radii for marker-driven Atlantis dungeon mobs.
     *
     * By default these are set equal (sphere). To make an ellipsoid, increase XZ while decreasing Y.
     */
    public static final int SPAWN_RADIUS_XZ = 48;
    public static final int SPAWN_RADIUS_Y = 48;
    public static final int DESPAWN_RADIUS_XZ = 64;
    public static final int DESPAWN_RADIUS_Y = 64;

    private static final ProximityMobManager INSTANCE = new ProximityMobManager();

    private final Map<ResourceKey<Level>, Map<BlockPos, UUID>> activeMobs = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<UUID, BlockPos>> activeMobPositions = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> nextRebindAttemptTickByMarker = new HashMap<>();
    private final Map<ResourceKey<Level>, Integer> worldTickCounter = new HashMap<>();
    private final Map<ResourceKey<Level>, Long> lastNoSpawnReasonLogMs = new HashMap<>();
    private final Map<ResourceKey<Level>, String> lastNoSpawnReasonByWorld = new HashMap<>();
    private final Set<String> externalPauseTokens = new HashSet<>();
    private final Path cycleStatePath = CyclePaths.stateFile();
    private long lastSpawnPhaseRefreshMs;
    private long cachedCycleStateModifiedMs = Long.MIN_VALUE;
    private CycleState cachedCycleState;
    private boolean spawnPhaseAllowsActivation = true;
    private String spawnPhaseBlockReason = "allowed";

    private static final long NO_SPAWN_REASON_LOG_INTERVAL_MS = 5_000L;
    private static final long NO_SPAWN_REASON_SAME_REASON_LOG_INTERVAL_MS = 60_000L;
    private static final long REBIND_RETRY_DELAY_TICKS = 100L;

    private boolean initialized;

    private ProximityMobManager() {
    }

    public static ProximityMobManager getInstance() {
        return INSTANCE;
    }

    public void register() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server == null) {
                return;
            }
            for (ServerLevel world : server.getAllLevels()) {
                onWorldTick(world);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onMobDeath(entity));
        AtlantisMod.LOGGER.info(
            "Atlantis proximity mob manager enabled (spawnRadiusXZ={} spawnRadiusY={} despawnRadiusXZ={} despawnRadiusY={})",
            SPAWN_RADIUS_XZ,
            SPAWN_RADIUS_Y,
            DESPAWN_RADIUS_XZ,
            DESPAWN_RADIUS_Y
        );
    }

    public void onWorldTick(ServerLevel world) {
        if (world == null) {
            return;
        }

        if (world.dimension() != Level.OVERWORLD) {
            return;
        }

        ResourceKey<Level> worldKey = world.dimension();
        int tick = worldTickCounter.getOrDefault(worldKey, 0) + 1;
        worldTickCounter.put(worldKey, tick);
        if ((tick % 5) != 0) {
            return;
        }

        AtlantisMobMarkerState state = AtlantisMobMarkerState.get(world);
        Map<BlockPos, UUID> active = getActiveMap(world);
        long worldTime = world.getGameTime();
        boolean allowActivation = isActivationPhaseEnabled(world.getServer());

        List<ServerPlayer> players = world.getPlayers(player -> player != null && !player.isSpectator() && player.isAlive());
        if (players.isEmpty()) {
            despawnAllActive(world, state, active);
            maybeLogNoSpawnReason(world, "no_alive_players", 0, active.size(), state.getTotalMarkers());
            return;
        }
        List<BlockPos> livePlayerPositions = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            livePlayerPositions.add(player.blockPosition());
        }

        int chunkRadius = Math.max(1, (DESPAWN_RADIUS_XZ >> 4) + 1);
        Set<BlockPos> shouldBeSpawned = new HashSet<>();

        for (ServerPlayer player : players) {
            BlockPos playerPos = player.blockPosition();
            state.forEachMarkerNear(playerPos, chunkRadius, (markerPos, marker) -> {
                if (isWithinEllipsoid(markerPos, playerPos, SPAWN_RADIUS_XZ, SPAWN_RADIUS_Y)) {
                    shouldBeSpawned.add(markerPos.immutable());
                }
            });
        }

        if (!allowActivation) {
            maybeLogNoSpawnReason(
                world,
                "activation_blocked:" + spawnPhaseBlockReason,
                shouldBeSpawned.size(),
                active.size(),
                state.getTotalMarkers()
            );
        } else if (shouldBeSpawned.isEmpty()) {
            maybeLogNoSpawnReason(world, "no_markers_in_player_radius", 0, active.size(), state.getTotalMarkers());
        }

        for (BlockPos pos : shouldBeSpawned) {
            if (!allowActivation) {
                continue;
            }

            UUID existingId = active.get(pos);
            if (existingId != null || active.containsKey(pos)) {
                Entity existing = existingId == null ? null : world.getEntity(existingId);
                if (existing instanceof Mob mob && mob.isAlive()) {
                    continue;
                }
                active.remove(pos);
                if (existingId != null) {
                    getActivePositionMap(world).remove(existingId);
                }
            }

            AtlantisMobMarker marker = state.getMarker(pos);
            if (marker != null) {
                if (shouldAttemptRebind(world, pos, worldTime)) {
                    if (bindExistingMobAtMarker(world, pos) != null) {
                        clearRebindAttempt(world, pos);
                        continue;
                    }
                    recordFailedRebindAttempt(world, pos, worldTime + REBIND_RETRY_DELAY_TICKS);
                }

                Mob spawned = spawnFromMarker(world, pos, marker);
                if (spawned != null) {
                    clearRebindAttempt(world, pos);
                }
            }
        }

        Iterator<Map.Entry<BlockPos, UUID>> activeIterator = active.entrySet().iterator();
        while (activeIterator.hasNext()) {
            Map.Entry<BlockPos, UUID> entry = activeIterator.next();
            BlockPos pos = entry.getKey();
            UUID uuid = entry.getValue();

            Entity entity = uuid == null ? null : world.getEntity(uuid);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                activeIterator.remove();
                if (uuid != null) {
                    getActivePositionMap(world).remove(uuid);
                }
                continue;
            }

            if (isWithinEllipsoidOfAnyPlayer(pos, livePlayerPositions, DESPAWN_RADIUS_XZ, DESPAWN_RADIUS_Y)) {
                continue;
            }

            AtlantisMobMarker marker = AtlantisMobMarker.fromEntity(mob);
            if (marker != null) {
                state.putMarker(pos.immutable(), marker);
            }
            mob.discard();
            if (uuid != null) {
                getActivePositionMap(world).remove(uuid);
            }
            state.setDirty();
            activeIterator.remove();
        }
    }

    public Mob spawnFromMarker(ServerLevel world, BlockPos pos, AtlantisMobMarker marker) {
        if (world == null || pos == null || marker == null) {
            return null;
        }

        if (!world.areEntitiesLoaded(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4))) {
            return null;
        }

        Mob mob = marker.createMob(world);
        if (mob == null) {
            return null;
        }

        float spawnYaw = marker.yaw();
        float spawnPitch = marker.pitch();
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, spawnYaw, spawnPitch);
        mob.setYRot(spawnYaw);
        mob.setXRot(spawnPitch);
        mob.setYBodyRot(spawnYaw);
        mob.setYHeadRot(spawnYaw);
        mob.addTag(SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG);
        mob.setPersistenceRequired();

        if (world.addFreshEntity(mob)) {
            BlockPos markerPos = pos.immutable();
            UUID mobId = mob.getUUID();
            getActiveMap(world).put(markerPos, mobId);
            getActivePositionMap(world).put(mobId, markerPos);
            return mob;
        }

        return null;
    }

    public void despawnToMarker(ServerLevel world, BlockPos pos, Mob mob) {
        if (world == null || pos == null || mob == null) {
            return;
        }

        AtlantisMobMarkerState state = AtlantisMobMarkerState.get(world);
        AtlantisMobMarker marker = AtlantisMobMarker.fromEntity(mob);
        if (marker != null) {
            state.putMarker(pos.immutable(), marker);
        }

        Map<BlockPos, UUID> active = getActiveMap(world);
        UUID removedId = active.remove(pos);
        if (removedId != null) {
            getActivePositionMap(world).remove(removedId);
        }
        clearRebindAttempt(world, pos);
        mob.discard();
        state.setDirty();
    }

    public void clearWithinBounds(ServerLevel world, ActiveConstructBounds bounds) {
        if (world == null || bounds == null) {
            return;
        }

        Map<BlockPos, UUID> active = getActiveMap(world);
        Iterator<Map.Entry<BlockPos, UUID>> activeIterator = active.entrySet().iterator();
        while (activeIterator.hasNext()) {
            Map.Entry<BlockPos, UUID> entry = activeIterator.next();
            BlockPos pos = entry.getKey();
            if (!bounds.contains(pos)) {
                continue;
            }

            UUID uuid = entry.getValue();
            Entity entity = uuid == null ? null : world.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
            activeIterator.remove();
            if (uuid != null) {
                getActivePositionMap(world).remove(uuid);
            }
            clearRebindAttempt(world, pos);
        }

        AtlantisMobMarkerState state = AtlantisMobMarkerState.get(world);
        state.removeInsideBounds(bounds);
    }

    public int countActive(ServerLevel world) {
        if (world == null) {
            return 0;
        }
        return getActiveMap(world).size();
    }

    public synchronized void acquireExternalPause(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (externalPauseTokens.add(token)) {
            AtlantisMod.LOGGER.info("[spawn] external pause acquired token={} activePauses={}", token, externalPauseTokens.size());
        }
    }

    public synchronized void releaseExternalPause(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (externalPauseTokens.remove(token)) {
            AtlantisMod.LOGGER.info("[spawn] external pause released token={} activePauses={}", token, externalPauseTokens.size());
        }
    }

    private synchronized boolean hasExternalPause() {
        return !externalPauseTokens.isEmpty();
    }

    public synchronized Set<String> getActivePauseTokens() {
        return Set.copyOf(externalPauseTokens);
    }

    public synchronized void clearAllExternalPauses() {
        int count = externalPauseTokens.size();
        externalPauseTokens.clear();
        if (count > 0) {
            AtlantisMod.LOGGER.info("[spawn] cleared all external pause tokens count={}", count);
        }
    }

    private void onMobDeath(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel world)) {
            return;
        }

        if (!mob.entityTags().contains(SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG)) {
            return;
        }

        Map<BlockPos, UUID> active = getActiveMap(world);
        UUID deadId = mob.getUUID();
        AtlantisMobMarkerState state = AtlantisMobMarkerState.get(world);

        BlockPos trackedPos = getActivePositionMap(world).remove(deadId);

        if (trackedPos == null) {
            trackedPos = findNearestMarkerPosition(state, mob.blockPosition(), 2);
        }

        if (trackedPos == null) {
            return;
        }

        UUID removedId = active.remove(trackedPos);
        if (removedId != null && !removedId.equals(deadId)) {
            getActivePositionMap(world).remove(removedId);
        }
        state.removeMarker(trackedPos);
    }

    private void despawnAllActive(ServerLevel world, AtlantisMobMarkerState state, Map<BlockPos, UUID> active) {
        if (active.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<BlockPos, UUID>> activeIterator = active.entrySet().iterator();
        while (activeIterator.hasNext()) {
            Map.Entry<BlockPos, UUID> entry = activeIterator.next();
            BlockPos pos = entry.getKey();
            UUID uuid = entry.getValue();
            Entity entity = uuid == null ? null : world.getEntity(uuid);

            if (entity instanceof Mob mob && mob.isAlive()) {
                AtlantisMobMarker marker = AtlantisMobMarker.fromEntity(mob);
                if (marker != null) {
                    state.putMarker(pos.immutable(), marker);
                }
                mob.discard();
            }

            activeIterator.remove();
            if (uuid != null) {
                getActivePositionMap(world).remove(uuid);
            }
        }
    }

    private Map<BlockPos, UUID> getActiveMap(ServerLevel world) {
        return activeMobs.computeIfAbsent(world.dimension(), ignored -> new HashMap<>());
    }

    private Map<UUID, BlockPos> getActivePositionMap(ServerLevel world) {
        return activeMobPositions.computeIfAbsent(world.dimension(), ignored -> new HashMap<>());
    }

    private Map<BlockPos, Long> getRebindAttemptMap(ServerLevel world) {
        return nextRebindAttemptTickByMarker.computeIfAbsent(world.dimension(), ignored -> new HashMap<>());
    }

    private boolean shouldAttemptRebind(ServerLevel world, BlockPos markerPos, long nowTick) {
        long dueTick = getRebindAttemptMap(world).getOrDefault(markerPos, Long.MIN_VALUE);
        return nowTick >= dueTick;
    }

    private void recordFailedRebindAttempt(ServerLevel world, BlockPos markerPos, long nextAttemptTick) {
        getRebindAttemptMap(world).put(markerPos.immutable(), nextAttemptTick);
    }

    private void clearRebindAttempt(ServerLevel world, BlockPos markerPos) {
        getRebindAttemptMap(world).remove(markerPos);
    }

    private Mob bindExistingMobAtMarker(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }

        AABB box = new AABB(pos).inflate(0.9, 1.5, 0.9);
        List<Mob> candidates = world.getEntitiesOfClass(
            Mob.class,
            box,
            mob -> mob != null
                && mob.isAlive()
                && mob.entityTags().contains(SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG)
        );
        if (candidates.isEmpty()) {
            return null;
        }

        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY();
        double centerZ = pos.getZ() + 0.5;

        Mob selected = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (Mob candidate : candidates) {
            double distanceSq = candidate.distanceToSqr(centerX, centerY, centerZ);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                selected = candidate;
            }
        }

        if (selected == null) {
            return null;
        }

        BlockPos markerPos = pos.immutable();
        UUID selectedId = selected.getUUID();

        Map<BlockPos, UUID> active = getActiveMap(world);
        Map<UUID, BlockPos> positions = getActivePositionMap(world);

        BlockPos previousPos = positions.put(selectedId, markerPos);
        if (previousPos != null && !previousPos.equals(markerPos)) {
            active.remove(previousPos);
        }
        active.put(markerPos, selectedId);
        return selected;
    }

    private BlockPos findNearestMarkerPosition(AtlantisMobMarkerState state, BlockPos center, int maxDistance) {
        if (state == null || center == null || maxDistance < 0) {
            return null;
        }

        int maxDistanceSq = maxDistance * maxDistance;
        BlockPos[] nearest = new BlockPos[1];
        int[] bestDistanceSq = new int[] {Integer.MAX_VALUE};

        state.forEachMarkerNear(center, 1, (pos, marker) -> {
            int distanceSq = (int) pos.distSqr(center);
            if (distanceSq > maxDistanceSq || distanceSq >= bestDistanceSq[0]) {
                return;
            }
            bestDistanceSq[0] = distanceSq;
            nearest[0] = pos;
        });

        return nearest[0];
    }

    private boolean isWithinEllipsoidOfAnyPlayer(BlockPos markerPos, List<BlockPos> playerPositions, int radiusXZ, int radiusY) {
        for (BlockPos playerPos : playerPositions) {
            if (isWithinEllipsoid(markerPos, playerPos, radiusXZ, radiusY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ellipsoid distance check centered on {@code center}.
     *
     * Condition: (dx^2 + dz^2)/rxz^2 + (dy^2)/ry^2 <= 1
     */
    private static boolean isWithinEllipsoid(BlockPos pos, BlockPos center, int radiusXZ, int radiusY) {
        if (pos == null || center == null) {
            return false;
        }
        if (radiusXZ <= 0) {
            return false;
        }

        long dx = (long) pos.getX() - (long) center.getX();
        long dz = (long) pos.getZ() - (long) center.getZ();
        long dy = (long) pos.getY() - (long) center.getY();

        long dxz2 = dx * dx + dz * dz;

        if (radiusY <= 0) {
            return dy == 0 && dxz2 <= (long) radiusXZ * (long) radiusXZ;
        }

        long rxz2 = (long) radiusXZ * (long) radiusXZ;
        long ry2 = (long) radiusY * (long) radiusY;

        // Compare without division: dxz2/rv^2 + dy^2/rxz^2 <= 1
        // => dxz2*ry2 + dy^2*rxz2 <= rxz2*ry2
        long left = dxz2 * ry2 + (dy * dy) * rxz2;
        long right = rxz2 * ry2;
        return left <= right;
    }

    private boolean isActivationPhaseEnabled(net.minecraft.server.MinecraftServer server) {
        if (hasExternalPause()) {
            spawnPhaseAllowsActivation = false;
            spawnPhaseBlockReason = "external_pause";
            return false;
        }

        long now = System.currentTimeMillis();
        if (lastSpawnPhaseRefreshMs != 0L && (now - lastSpawnPhaseRefreshMs) < 1000L) {
            return spawnPhaseAllowsActivation;
        }
        lastSpawnPhaseRefreshMs = now;

        if (server == null) {
            spawnPhaseAllowsActivation = true;
            spawnPhaseBlockReason = "allowed(server_null)";
            return true;
        }

        CycleState state = getCachedCycleState();
        if (state == null || !state.enabled()) {
            spawnPhaseAllowsActivation = true;
            spawnPhaseBlockReason = "allowed(cycle_disabled_or_missing)";
            return true;
        }

        spawnPhaseAllowsActivation = CycleState.Stage.WAIT_BEFORE_UNDO.name().equals(state.stage());
        spawnPhaseBlockReason = spawnPhaseAllowsActivation
            ? "allowed(cycle_wait_before_undo)"
            : "cycle_stage=" + state.stage();
        return spawnPhaseAllowsActivation;
    }

    private CycleState getCachedCycleState() {
        try {
            if (!Files.exists(cycleStatePath)) {
                cachedCycleState = null;
                cachedCycleStateModifiedMs = Long.MIN_VALUE;
                return null;
            }

            long modified = Files.getLastModifiedTime(cycleStatePath).toMillis();
            if (modified == cachedCycleStateModifiedMs) {
                return cachedCycleState;
            }

            cachedCycleState = CycleJsonIO.tryRead(cycleStatePath);
            cachedCycleStateModifiedMs = modified;
            return cachedCycleState;
        } catch (Exception ignored) {
            return cachedCycleState;
        }
    }

    private void maybeLogNoSpawnReason(ServerLevel world, String reason, int nearbyMarkers, int activeCount, int totalMarkers) {
        if (world == null || reason == null || reason.isBlank()) {
            return;
        }

        ResourceKey<Level> worldKey = world.dimension();
        long now = System.currentTimeMillis();
        long last = lastNoSpawnReasonLogMs.getOrDefault(worldKey, 0L);
        String previousReason = lastNoSpawnReasonByWorld.get(worldKey);
        boolean sameReason = reason.equals(previousReason);
        long interval = sameReason
            ? NO_SPAWN_REASON_SAME_REASON_LOG_INTERVAL_MS
            : NO_SPAWN_REASON_LOG_INTERVAL_MS;

        if (last != 0L && (now - last) < interval) {
            return;
        }

        lastNoSpawnReasonLogMs.put(worldKey, now);
        lastNoSpawnReasonByWorld.put(worldKey, reason);
        AtlantisMod.LOGGER.info(
            "[spawn] idle reason={} dim={} markersNearPlayers={} active={} totalMarkers={}",
            reason,
            world.dimension().identifier(),
            nearbyMarkers,
            activeCount,
            totalMarkers
        );
    }
}
