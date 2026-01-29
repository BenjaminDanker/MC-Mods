package com.silver.skyislands.enderdragons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks player-placed dragon heads (and wall heads) by position.
 *
 * <p>Dragon heads are not block entities in modern versions, so we track placement/break events
 * and persist a set of known head coordinates to JSON.
 */
final class DragonHeadTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DragonHeadTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long FLUSH_INTERVAL_TICKS = 20L * 60L; // ~1 minute
    private static final long VALIDATE_INTERVAL_TICKS = 20L * 60L; // ~1 minute

    private final Path path;

    // dimensionId -> packed BlockPos longs
    private final Map<String, LongOpenHashSet> headsByDimension = new HashMap<>();
    // dimensionId -> chunkKey -> packed BlockPos longs
    private final Map<String, Map<Long, LongOpenHashSet>> chunkIndexByDimension = new HashMap<>();

    private boolean dirty;
    private long nextFlushTick;
    private long nextValidateTick;

    DragonHeadTracker() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-dragon-heads.json");
        load();
    }

    void tick(MinecraftServer server, long nowTick) {
        if (server == null) {
            return;
        }

        if (nowTick >= nextValidateTick) {
            nextValidateTick = nowTick + VALIDATE_INTERVAL_TICKS;
            validateKnownHeads(server);
        }

        if (dirty && nowTick >= nextFlushTick) {
            nextFlushTick = nowTick + FLUSH_INTERVAL_TICKS;
            flush();
        }
    }

    void onPossiblePlaced(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        BlockState bs = world.getBlockState(pos);
        if (!isDragonHead(bs)) {
            return;
        }
        add(world, pos);
    }

    void onPossibleBroken(ServerWorld world, BlockPos pos, BlockState previousState) {
        if (world == null || pos == null) {
            return;
        }
        if (previousState != null && !isDragonHead(previousState)) {
            return;
        }

        String dim = dimId(world);
        long packed = pos.toImmutable().asLong();
        LongOpenHashSet set = headsByDimension.get(dim);
        if (set == null || !set.contains(packed)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][heads] break head-not-tracked dim={} pos={} prev={}", dim, pos, previousState == null ? "<unknown>" : previousState.getBlock());
            }
        }

        remove(world, pos);
    }

    boolean isStillHead(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        String dim = dimId(world);
        LongSet set = headsByDimension.get(dim);
        if (set == null || !set.contains(pos.asLong())) {
            return false;
        }

        // If the chunk is loaded, validate immediately (cheap and keeps behavior crisp).
        if (world.isChunkLoaded(pos)) {
            if (!isDragonHead(world.getBlockState(pos))) {
                remove(world, pos);
                return false;
            }
        }

        return true;
    }

    List<BlockPos> findHeadsNear(ServerWorld world, BlockPos center, int radius, int vertical, int maxFound) {
        if (world == null || center == null || radius <= 0 || maxFound <= 0) {
            return List.of();
        }

        String dim = dimId(world);
        Map<Long, LongOpenHashSet> byChunk = chunkIndexByDimension.get(dim);
        if (byChunk == null || byChunk.isEmpty()) {
            return List.of();
        }

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        int minY = cy - vertical;
        int maxY = cy + vertical;

        double radiusSq = (double) radius * (double) radius;

        int minChunkX = (cx - radius) >> 4;
        int maxChunkX = (cx + radius) >> 4;
        int minChunkZ = (cz - radius) >> 4;
        int maxChunkZ = (cz + radius) >> 4;

        List<BlockPos> found = new ArrayList<>(Math.min(maxFound, 32));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long ck = ChunkPos.toLong(chunkX, chunkZ);
                LongOpenHashSet positions = byChunk.get(ck);
                if (positions == null || positions.isEmpty()) {
                    continue;
                }

                LongIterator it = positions.iterator();
                while (it.hasNext()) {
                    long packed = it.nextLong();
                    BlockPos p = BlockPos.fromLong(packed);
                    int y = p.getY();
                    if (y < minY || y > maxY) {
                        continue;
                    }

                    int dx = p.getX() - cx;
                    int dz = p.getZ() - cz;
                    double dist2 = (double) dx * (double) dx + (double) dz * (double) dz;
                    if (dist2 > radiusSq) {
                        continue;
                    }

                    found.add(p);
                    if (found.size() >= maxFound) {
                        return found;
                    }
                }
            }
        }

        return found;
    }

    void flush() {
        if (!dirty) {
            return;
        }

        try {
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            JsonObject dims = new JsonObject();

            for (Map.Entry<String, LongOpenHashSet> e : headsByDimension.entrySet()) {
                JsonArray arr = new JsonArray();
                for (long packed : e.getValue()) {
                    // Store as string to avoid JSON number precision issues.
                    arr.add(Long.toString(packed));
                }
                dims.add(e.getKey(), arr);
            }

            root.add("dimensions", dims);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;

            if (LOGGER.isDebugEnabled()) {
                int total = 0;
                for (LongOpenHashSet set : headsByDimension.values()) {
                    total += set.size();
                }
                LOGGER.debug("[Sky-Islands][dragons][heads] flush ok path={} dims={} totalHeads={}", path, headsByDimension.size(), total);
            }
        } catch (IOException e) {
            LOGGER.warn("[Sky-Islands][dragons][heads] flush failed path={} err={}", path, e.toString());
        }
    }

    private void add(ServerWorld world, BlockPos pos) {
        String dim = dimId(world);
        long packed = pos.toImmutable().asLong();

        LongOpenHashSet set = headsByDimension.computeIfAbsent(dim, ignored -> new LongOpenHashSet());
        if (!set.add(packed)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Sky-Islands][dragons][heads] add ignored (already tracked) dim={} pos={}", dim, pos);
            }
            return;
        }

        indexAdd(dim, packed);
        dirty = true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][heads] add dim={} pos={}", dim, pos);
        }
    }

    private void remove(ServerWorld world, BlockPos pos) {
        String dim = dimId(world);
        long packed = pos.toImmutable().asLong();

        LongOpenHashSet set = headsByDimension.get(dim);
        if (set == null || !set.remove(packed)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[Sky-Islands][dragons][heads] remove ignored (not tracked) dim={} pos={}", dim, pos);
            }
            return;
        }

        indexRemove(dim, packed);
        dirty = true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][heads] remove dim={} pos={}", dim, pos);
        }
    }

    private void validateKnownHeads(MinecraftServer server) {
        int removed = 0;
        int checked = 0;

        for (ServerWorld world : server.getWorlds()) {
            String dim = dimId(world);
            LongOpenHashSet set = headsByDimension.get(dim);
            if (set == null || set.isEmpty()) {
                continue;
            }

            LongIterator it = set.iterator();
            while (it.hasNext()) {
                long packed = it.nextLong();
                BlockPos pos = BlockPos.fromLong(packed);

                // Avoid loading chunks; only validate if currently loaded.
                if (!world.isChunkLoaded(pos)) {
                    continue;
                }

                checked++;

                if (!isDragonHead(world.getBlockState(pos))) {
                    it.remove();
                    indexRemove(dim, packed);
                    dirty = true;
                    removed++;
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][heads] validate checkedLoaded={} removedMissing={}", checked, removed);
        }
    }

    private void load() {
        headsByDimension.clear();
        chunkIndexByDimension.clear();
        dirty = false;

        if (!Files.exists(path)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][heads] load skipped (missing) path={}", path);
            }
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject dims = root.has("dimensions") && root.get("dimensions").isJsonObject() ? root.getAsJsonObject("dimensions") : new JsonObject();

            for (Map.Entry<String, JsonElement> entry : dims.entrySet()) {
                if (!entry.getValue().isJsonArray()) {
                    continue;
                }

                String dim = entry.getKey();
                JsonArray arr = entry.getValue().getAsJsonArray();
                LongOpenHashSet set = new LongOpenHashSet();

                for (JsonElement el : arr) {
                    if (!el.isJsonPrimitive()) {
                        continue;
                    }
                    try {
                        long packed = Long.parseLong(el.getAsString());
                        set.add(packed);
                    } catch (Exception ignored) {
                    }
                }

                if (!set.isEmpty()) {
                    headsByDimension.put(dim, set);
                    for (long packed : set) {
                        indexAdd(dim, packed);
                    }
                }
            }

            if (LOGGER.isDebugEnabled()) {
                int total = 0;
                for (LongOpenHashSet set : headsByDimension.values()) {
                    total += set.size();
                }
                LOGGER.debug("[Sky-Islands][dragons][heads] load ok dims={} totalHeads={}", headsByDimension.size(), total);
            }
        } catch (Exception e) {
            LOGGER.warn("[Sky-Islands][dragons][heads] load failed path={} err={}", path, e.toString());
        }
    }

    private void indexAdd(String dim, long packedPos) {
        BlockPos pos = BlockPos.fromLong(packedPos);
        long ck = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);

        Map<Long, LongOpenHashSet> byChunk = chunkIndexByDimension.computeIfAbsent(dim, ignored -> new HashMap<>());
        byChunk.computeIfAbsent(ck, ignored -> new LongOpenHashSet()).add(packedPos);
    }

    private void indexRemove(String dim, long packedPos) {
        Map<Long, LongOpenHashSet> byChunk = chunkIndexByDimension.get(dim);
        if (byChunk == null) {
            return;
        }
        BlockPos pos = BlockPos.fromLong(packedPos);
        long ck = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);

        LongOpenHashSet set = byChunk.get(ck);
        if (set == null) {
            return;
        }
        set.remove(packedPos);
        if (set.isEmpty()) {
            byChunk.remove(ck);
        }
    }

    private static boolean isDragonHead(BlockState state) {
        return state != null && (state.isOf(Blocks.DRAGON_HEAD) || state.isOf(Blocks.DRAGON_WALL_HEAD));
    }

    private static String dimId(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }
}
