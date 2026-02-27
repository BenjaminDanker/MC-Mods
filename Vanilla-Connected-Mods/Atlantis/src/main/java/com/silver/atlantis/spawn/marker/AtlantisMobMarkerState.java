package com.silver.atlantis.spawn.marker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Persistent marker storage for Atlantis proximity spawn system.
 */
public final class AtlantisMobMarkerState extends PersistentState {

    private static final String STATE_KEY = "atlantis_mob_markers";

    private record MarkerEntry(long packedPos, AtlantisMobMarker marker) {
        private static final Codec<MarkerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("packedPos").forGetter(MarkerEntry::packedPos),
            AtlantisMobMarker.CODEC.fieldOf("marker").forGetter(MarkerEntry::marker)
        ).apply(instance, MarkerEntry::new));
    }

    private static final Codec<AtlantisMobMarkerState> CODEC = MarkerEntry.CODEC.listOf().xmap(
        AtlantisMobMarkerState::new,
        AtlantisMobMarkerState::toEntries
    );

    private static final PersistentStateType<AtlantisMobMarkerState> TYPE =
        new PersistentStateType<>(STATE_KEY, AtlantisMobMarkerState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<Long, Map<BlockPos, AtlantisMobMarker>> markersByChunk = new HashMap<>();

    public AtlantisMobMarkerState() {
    }

    private AtlantisMobMarkerState(List<MarkerEntry> entries) {
        if (entries == null) {
            return;
        }

        for (MarkerEntry entry : entries) {
            if (entry == null || entry.marker() == null) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(entry.packedPos());
            putInternal(pos, entry.marker());
        }
    }

    public static AtlantisMobMarkerState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE);
    }

    public AtlantisMobMarker getMarker(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<BlockPos, AtlantisMobMarker> byPos = markersByChunk.get(chunkKey);
        if (byPos == null) {
            return null;
        }
        return byPos.get(pos);
    }

    public void putMarker(BlockPos pos, AtlantisMobMarker marker) {
        if (pos == null || marker == null) {
            return;
        }

        putInternal(pos.toImmutable(), marker);
        markDirty();
    }

    public void removeMarker(BlockPos pos) {
        if (pos == null) {
            return;
        }

        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<BlockPos, AtlantisMobMarker> byPos = markersByChunk.get(chunkKey);
        if (byPos == null) {
            return;
        }

        if (byPos.remove(pos) != null) {
            if (byPos.isEmpty()) {
                markersByChunk.remove(chunkKey);
            }
            markDirty();
        }
    }

    public int removeInsideBounds(ActiveConstructBounds bounds) {
        if (bounds == null) {
            return 0;
        }

        int removed = 0;
        List<Long> emptyChunks = new ArrayList<>();
        for (Map.Entry<Long, Map<BlockPos, AtlantisMobMarker>> chunkEntry : markersByChunk.entrySet()) {
            Map<BlockPos, AtlantisMobMarker> byPos = chunkEntry.getValue();
            if (byPos == null || byPos.isEmpty()) {
                emptyChunks.add(chunkEntry.getKey());
                continue;
            }

            List<BlockPos> toRemove = new ArrayList<>();
            for (BlockPos pos : byPos.keySet()) {
                if (bounds.contains(pos)) {
                    toRemove.add(pos);
                }
            }

            for (BlockPos pos : toRemove) {
                byPos.remove(pos);
                removed++;
            }

            if (byPos.isEmpty()) {
                emptyChunks.add(chunkEntry.getKey());
            }
        }

        for (Long chunkKey : emptyChunks) {
            markersByChunk.remove(chunkKey);
        }

        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    public Map<BlockPos, AtlantisMobMarker> getMarkersNear(BlockPos center, int chunkRadius) {
        if (center == null || chunkRadius < 0) {
            return Map.of();
        }

        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        Map<BlockPos, AtlantisMobMarker> out = new HashMap<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long chunkKey = ChunkPos.toLong(centerChunkX + dx, centerChunkZ + dz);
                Map<BlockPos, AtlantisMobMarker> byPos = markersByChunk.get(chunkKey);
                if (byPos == null || byPos.isEmpty()) {
                    continue;
                }
                out.putAll(byPos);
            }
        }

        return out;
    }

    public void forEachMarkerNear(BlockPos center, int chunkRadius, BiConsumer<BlockPos, AtlantisMobMarker> consumer) {
        if (center == null || chunkRadius < 0 || consumer == null) {
            return;
        }

        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long chunkKey = ChunkPos.toLong(centerChunkX + dx, centerChunkZ + dz);
                Map<BlockPos, AtlantisMobMarker> byPos = markersByChunk.get(chunkKey);
                if (byPos == null || byPos.isEmpty()) {
                    continue;
                }

                for (Map.Entry<BlockPos, AtlantisMobMarker> entry : byPos.entrySet()) {
                    consumer.accept(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public int getTotalMarkers() {
        int total = 0;
        for (Map<BlockPos, AtlantisMobMarker> byPos : markersByChunk.values()) {
            if (byPos != null) {
                total += byPos.size();
            }
        }
        return total;
    }

    private void putInternal(BlockPos pos, AtlantisMobMarker marker) {
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        markersByChunk.computeIfAbsent(chunkKey, ignored -> new HashMap<>()).put(pos, marker);
    }

    private List<MarkerEntry> toEntries() {
        List<MarkerEntry> entries = new ArrayList<>();
        for (Map<BlockPos, AtlantisMobMarker> byPos : markersByChunk.values()) {
            if (byPos == null || byPos.isEmpty()) {
                continue;
            }

            for (Map.Entry<BlockPos, AtlantisMobMarker> entry : byPos.entrySet()) {
                entries.add(new MarkerEntry(entry.getKey().asLong(), entry.getValue()));
            }
        }
        return entries;
    }
}
