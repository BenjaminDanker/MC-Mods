package com.silver.arcade.realm;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Edits the saved data of chunks that are not loaded (read and write through the chunk map's IO, like the Atlantis mod's
 * UnloadedChunkNbtEditor, whose section palette decode/encode this is adapted from). Everything here runs off the server
 * thread. An edited chunk loses its light data and its heightmaps and is marked as not lit, so the server relights it
 * when it loads.
 */
final class ChunkEdit {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    private static final long TIMEOUT_SECONDS = 45L;
    private static final int MIN_SECTION = -4;
    private static final int MAX_SECTION = 19;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private ChunkEdit() {
    }

    static CompletableFuture<Optional<CompoundTag>> read(ServerLevel world, ChunkPos pos) {
        return world.getChunkSource().chunkMap.read(pos).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static CompletableFuture<?> write(ServerLevel world, ChunkPos pos, CompoundTag nbt) {
        return world.getChunkSource().chunkMap.write(pos, () -> nbt).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------------------------------------------------
    // a chunk that was never generated: a copy of an existing (void) chunk with its blocks stripped

    private static CompoundTag template;

    /** Blocking; call from a worker thread. Reads an existing chunk of the arena slots (any chunk will do) once. */
    static synchronized CompoundTag template(ServerLevel world) {
        if (template != null) {
            return template;
        }
        CompoundTag found = null;
        for (int slot = 0; slot < 400 && found == null; slot++) {
            try {
                Optional<CompoundTag> chunk = read(world, new ChunkPos((Arena.SPACING * (slot + 1)) >> 4, 0)).join();
                if (chunk.isPresent()) {
                    found = stripped(chunk.get().copy());
                }
            } catch (RuntimeException failure) {
                // try the next one
            }
        }
        if (found == null) {
            LOGGER.warn("Arcade: no existing chunk to copy as a template; building the empty chunk from scratch");
            found = new CompoundTag();
            found.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
            found.putInt("yPos", MIN_SECTION);
            found.putString("Status", "minecraft:full");
            found.putLong("LastUpdate", 0L);
            found.putLong("InhabitedTime", 0L);
            found.put("sections", new ListTag());
            found.put("block_entities", new ListTag());
            CompoundTag structures = new CompoundTag();
            structures.put("starts", new CompoundTag());
            structures.put("References", new CompoundTag());
            found.put("structures", structures);
        }
        template = found;
        return template;
    }

    /** No blocks, no block entities, no light, no heightmaps, no ticks; the sections keep their biomes. */
    private static CompoundTag stripped(CompoundTag chunk) {
        ListTag sections = chunk.getListOrEmpty("sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompoundOrEmpty(i);
            CompoundTag blockStates = new CompoundTag();
            ListTag palette = new ListTag();
            palette.add(NbtUtils.writeBlockState(AIR));
            blockStates.put("palette", palette);
            section.put("block_states", blockStates);
            section.remove("SkyLight");
            section.remove("BlockLight");
        }
        chunk.put("block_entities", new ListTag());
        for (String key : new String[] {"Heightmaps", "block_ticks", "fluid_ticks", "PostProcessing", "blending_data", "CarvingMasks", "Lights"}) {
            chunk.remove(key);
        }
        chunk.putByte("isLightOn", (byte) 0);
        chunk.putLong("InhabitedTime", 0L);
        return chunk;
    }

    static CompoundTag fresh(ServerLevel world, ChunkPos pos) {
        CompoundTag chunk = template(world).copy();
        chunk.putInt("xPos", pos.x());
        chunk.putInt("zPos", pos.z());
        return chunk;
    }

    // ---------------------------------------------------------------------------------------------------------
    // block entities: a block that has one gets an empty entry, so clients see it (chests, beds, signs, banners, heads)

    private static final Map<Block, String> BLOCK_ENTITY_IDS = new ConcurrentHashMap<>();
    private static final String NONE = "";

    private static String blockEntityId(BlockState state) {
        if (!state.hasBlockEntity()) {
            return null;
        }
        String id = BLOCK_ENTITY_IDS.computeIfAbsent(state.getBlock(), block -> {
            try {
                if (block instanceof EntityBlock entityBlock) {
                    var entity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
                    if (entity != null) {
                        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
                        if (key != null) {
                            return key.toString();
                        }
                    }
                }
            } catch (Throwable failure) {
                LOGGER.warn("Arcade: no block entity id for {}", block, failure);
            }
            return NONE;
        });
        return id.isEmpty() ? null : id;
    }

    // ---------------------------------------------------------------------------------------------------------
    // applying block changes to a chunk's data

    /**
     * Sets blocks[i] at positions[i] (world block positions inside this chunk), 0 &lt;= i &lt; count.
     * Air clears (and drops the block entity entry). Light data and heightmaps are dropped.
     */
    static void apply(CompoundTag chunk, long[] positions, BlockState[] states, int count) {
        ListTag sections = chunk.getListOrEmpty("sections");
        if (!chunk.contains("sections")) {
            chunk.put("sections", sections);
        }
        ListTag blockEntityList = chunk.getListOrEmpty("block_entities");
        Long2ObjectOpenHashMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < blockEntityList.size(); i++) {
            CompoundTag be = blockEntityList.getCompoundOrEmpty(i);
            blockEntities.put(BlockPos.asLong(be.getIntOr("x", 0), be.getIntOr("y", 0), be.getIntOr("z", 0)), be);
        }
        Map<Integer, SectionData> bySection = new HashMap<>();
        for (int i = 0; i < count; i++) {
            long key = positions[i];
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            int sectionY = Math.floorDiv(y, 16);
            if (sectionY < MIN_SECTION || sectionY > MAX_SECTION) {
                continue;
            }
            SectionData section = bySection.get(sectionY);
            if (section == null) {
                section = SectionData.of(sections, sectionY);
                bySection.put(sectionY, section);
            }
            BlockState state = states[i];
            section.set(x & 15, y & 15, z & 15, state);
            blockEntities.remove(key);
            String id = blockEntityId(state);
            if (id != null) {
                CompoundTag be = new CompoundTag();
                be.putString("id", id);
                be.putInt("x", x);
                be.putInt("y", y);
                be.putInt("z", z);
                blockEntities.put(key, be);
            }
        }
        for (SectionData section : bySection.values()) {
            section.flush(sections);
        }
        ListTag newList = new ListTag();
        blockEntities.values().forEach(newList::add);
        chunk.put("block_entities", newList);
        // relight on load: no light data in any section, no heightmaps, not lit
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompoundOrEmpty(i);
            section.remove("SkyLight");
            section.remove("BlockLight");
        }
        chunk.remove("Heightmaps");
        chunk.putByte("isLightOn", (byte) 0);
    }

    static String toBlockString(CompoundTag stateNbt) {
        String name = stateNbt.getStringOr("Name", "minecraft:air");
        CompoundTag props = stateNbt.getCompoundOrEmpty("Properties");
        if (props.isEmpty()) {
            return name;
        }
        List<String> keys = new ArrayList<>(props.keySet());
        keys.sort(String::compareTo);
        StringBuilder out = new StringBuilder(name).append('[');
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(keys.get(i)).append('=').append(props.getStringOr(keys.get(i), ""));
        }
        return out.append(']').toString();
    }

    private static final class SectionData {
        private final CompoundTag section;
        private final boolean isNew;
        private final List<CompoundTag> palette = new ArrayList<>();
        private final Map<String, Integer> paletteIndexByKey = new HashMap<>();
        private final IdentityHashMap<BlockState, Integer> indexByState = new IdentityHashMap<>();
        private final int[] states = new int[4096];

        private SectionData(CompoundTag section, boolean isNew) {
            this.section = section;
            this.isNew = isNew;
            CompoundTag blockStates = section.getCompoundOrEmpty("block_states");
            ListTag paletteNbt = blockStates.getListOrEmpty("palette");
            if (paletteNbt.isEmpty()) {
                CompoundTag air = NbtUtils.writeBlockState(AIR);
                palette.add(air);
                paletteIndexByKey.put(toBlockString(air), 0);
            } else {
                for (int i = 0; i < paletteNbt.size(); i++) {
                    CompoundTag state = paletteNbt.getCompoundOrEmpty(i).copy();
                    palette.add(state);
                    paletteIndexByKey.put(toBlockString(state), i);
                }
            }
            decodeStates(blockStates.getLongArray("data").orElse(new long[0]));
        }

        static SectionData of(ListTag sections, int sectionY) {
            for (int i = 0; i < sections.size(); i++) {
                CompoundTag section = sections.getCompoundOrEmpty(i);
                if (section.getByteOr("Y", (byte) 0) == (byte) sectionY) {
                    return new SectionData(section, false);
                }
            }
            CompoundTag created = new CompoundTag();
            created.putByte("Y", (byte) sectionY);
            return new SectionData(created, true);
        }

        void set(int lx, int ly, int lz, BlockState state) {
            Integer cached = indexByState.get(state);
            int index;
            if (cached != null) {
                index = cached;
            } else {
                CompoundTag nbt = NbtUtils.writeBlockState(state);
                String key = toBlockString(nbt);
                Integer existing = paletteIndexByKey.get(key);
                if (existing != null) {
                    index = existing;
                } else {
                    index = palette.size();
                    palette.add(nbt);
                    paletteIndexByKey.put(key, index);
                }
                indexByState.put(state, index);
            }
            states[(ly << 8) | (lz << 4) | lx] = index;
        }

        /** Rewrites the palette from the states in use (so cleared rooms leave nothing behind) and the packed data. */
        void flush(ListTag sections) {
            int[] remap = new int[palette.size()];
            java.util.Arrays.fill(remap, -1);
            List<CompoundTag> used = new ArrayList<>();
            int[] packedIndex = new int[4096];
            for (int i = 0; i < 4096; i++) {
                int old = states[i];
                if (remap[old] < 0) {
                    remap[old] = used.size();
                    used.add(palette.get(old));
                }
                packedIndex[i] = remap[old];
            }
            ListTag paletteNbt = new ListTag();
            used.forEach(state -> paletteNbt.add(state.copy()));
            CompoundTag blockStates = new CompoundTag();
            blockStates.put("palette", paletteNbt);
            if (used.size() > 1) {
                blockStates.putLongArray("data", encode(packedIndex, used.size()));
            }
            section.put("block_states", blockStates);
            if (isNew) {
                insertSorted(sections, section);
            }
        }

        private static void insertSorted(ListTag sections, CompoundTag section) {
            byte y = section.getByteOr("Y", (byte) 0);
            int at = sections.size();
            for (int i = 0; i < sections.size(); i++) {
                if (sections.getCompoundOrEmpty(i).getByteOr("Y", (byte) 0) > y) {
                    at = i;
                    break;
                }
            }
            sections.add(at, section);
        }

        private void decodeStates(long[] packed) {
            if (palette.size() <= 1) {
                return;
            }
            int bits = Math.max(4, ceilLog2(palette.size()));
            int valuesPerLong = Math.max(1, 64 / bits);
            long mask = (1L << bits) - 1L;
            for (int i = 0; i < states.length; i++) {
                int longIndex = i / valuesPerLong;
                int bitOffset = (i % valuesPerLong) * bits;
                if (longIndex >= packed.length) {
                    states[i] = 0;
                    continue;
                }
                int paletteIndex = (int) ((packed[longIndex] >>> bitOffset) & mask);
                states[i] = paletteIndex < palette.size() ? paletteIndex : 0;
            }
        }

        private static long[] encode(int[] values, int paletteSize) {
            int bits = Math.max(4, ceilLog2(paletteSize));
            int valuesPerLong = Math.max(1, 64 / bits);
            long[] packed = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
            long mask = (1L << bits) - 1L;
            for (int i = 0; i < values.length; i++) {
                int longIndex = i / valuesPerLong;
                int bitOffset = (i % valuesPerLong) * bits;
                packed[longIndex] = (packed[longIndex] & ~(mask << bitOffset)) | (((long) values[i] & mask) << bitOffset);
            }
            return packed;
        }

        private static int ceilLog2(int value) {
            return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1);
        }
    }
}
