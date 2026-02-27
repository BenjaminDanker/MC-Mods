package com.silver.atlantis.spawn.service;

import com.silver.atlantis.construct.OfflineChunkBlockReader;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.config.SpawnMobConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StructureMobMarkerDiscoveryService {

    enum RejectReason {
        NONE,
        OUT_OF_BOUNDS,
        FLOOR_OUT_OF_BOUNDS,
        CHUNK_NBT_UNAVAILABLE,
        UNSUPPORTED_FLOOR,
        NO_HEADROOM
    }

    static final class AirSpawnStats {
        int attempts;
        int noGlass;
        int tooShort;
        int notFound;
    }

    record DiscoveryResult(List<ProximitySpawnService.SpawnMarker> markers, RejectReason rejectReason) {
    }

    DiscoveryResult discoverMarker(long candidatePosKey,
                                   ActiveConstructBounds bounds,
                                   int easyY,
                                   ServerWorld world,
                                   Map<Long, Optional<OfflineChunkBlockReader.ChunkSnapshot>> offlineChunkCache,
                                   AirSpawnStats airStats) {
        BlockPos floorPos = BlockPos.fromLong(candidatePosKey);
        if (!bounds.contains(floorPos)) {
            return new DiscoveryResult(List.of(), RejectReason.OUT_OF_BOUNDS);
        }

        int floorY = floorPos.getY();
        int markerY = floorY + 1;
        if (markerY + 1 > bounds.maxY()) {
            return new DiscoveryResult(List.of(), RejectReason.FLOOR_OUT_OF_BOUNDS);
        }

        int x = floorPos.getX();
        int z = floorPos.getZ();
        long chunkKey = ChunkPos.toLong(x >> 4, z >> 4);

        Optional<OfflineChunkBlockReader.ChunkSnapshot> snapshotOpt = getOfflineSnapshot(offlineChunkCache, world, chunkKey, x >> 4, z >> 4);
        if (snapshotOpt.isEmpty()) {
            return new DiscoveryResult(List.of(), RejectReason.CHUNK_NBT_UNAVAILABLE);
        }
        OfflineChunkBlockReader.ChunkSnapshot snapshot = snapshotOpt.get();

        String floorBlockId = snapshot.blockStringAt(x, floorY, z);
        SpawnMobConfig.SpawnType spawnType = SpawnMarkerBlockClassifier.spawnTypeFor(floorBlockId);
        if (spawnType == null) {
            return new DiscoveryResult(List.of(), RejectReason.UNSUPPORTED_FLOOR);
        }

        String markerBlockId = snapshot.blockStringAt(x, markerY, z);
        String aboveTwoBlockId = snapshot.blockStringAt(x, markerY + 1, z);

        boolean hasSpace;
        if (spawnType == SpawnMobConfig.SpawnType.WATER) {
            hasSpace = SpawnMarkerBlockClassifier.isWaterLike(markerBlockId);
        } else {
            hasSpace = SpawnMarkerBlockClassifier.isAirOrWaterLike(markerBlockId)
                && SpawnMarkerBlockClassifier.isAirOrWaterLike(aboveTwoBlockId);
        }

        if (!hasSpace) {
            return new DiscoveryResult(List.of(), RejectReason.NO_HEADROOM);
        }

        List<ProximitySpawnService.SpawnMarker> discovered = new ArrayList<>(2);
        BlockPos markerPos = new BlockPos(x, markerY, z);
        discovered.add(new ProximitySpawnService.SpawnMarker(markerPos, spawnType));

        if (spawnType == SpawnMobConfig.SpawnType.LAND && markerPos.getY() > easyY) {
            Optional<BlockPos> airSpawnPos = findAirSpawnPositionOffline(snapshot, markerPos, bounds, airStats);
            airSpawnPos.ifPresent(pos -> discovered.add(new ProximitySpawnService.SpawnMarker(pos, SpawnMobConfig.SpawnType.AIR)));
        }

        return new DiscoveryResult(discovered, RejectReason.NONE);
    }

    private Optional<OfflineChunkBlockReader.ChunkSnapshot> getOfflineSnapshot(
        Map<Long, Optional<OfflineChunkBlockReader.ChunkSnapshot>> offlineChunkCache,
        ServerWorld world,
        long chunkKey,
        int chunkX,
        int chunkZ
    ) {
        Optional<OfflineChunkBlockReader.ChunkSnapshot> cached = offlineChunkCache.get(chunkKey);
        if (cached != null) {
            return cached;
        }

        Optional<OfflineChunkBlockReader.ChunkSnapshot> loaded = OfflineChunkBlockReader.load(world, new ChunkPos(chunkX, chunkZ));
        offlineChunkCache.put(chunkKey, loaded);
        return loaded;
    }

    private Optional<BlockPos> findAirSpawnPositionOffline(
        OfflineChunkBlockReader.ChunkSnapshot snapshot,
        BlockPos spawnPos,
        ActiveConstructBounds bounds,
        AirSpawnStats stats
    ) {
        if (snapshot == null || spawnPos == null || bounds == null) {
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
            String blockId = snapshot.blockStringAt(x, y, z);

            if (SpawnMarkerBlockClassifier.isAirLike(blockId)) {
                if (currentAirStart == Integer.MIN_VALUE) {
                    currentAirStart = y;
                }
                continue;
            }

            if ("minecraft:glass".equals(SpawnMarkerBlockClassifier.baseBlockName(blockId))) {
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
                return Optional.of(new BlockPos(x, spawnY, z));
            }

            currentAirStart = Integer.MIN_VALUE;
        }

        if (stats != null) {
            stats.notFound++;
        }
        return Optional.empty();
    }
}
