package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

final class UnloadedChunkNbtEditor {

    private static final long CHUNK_NBT_TIMEOUT_SECONDS = 45L;
    private static final AtomicBoolean MALFORMED_BLOCK_STRING_LOGGED = new AtomicBoolean(false);

    private UnloadedChunkNbtEditor() {
    }

    interface MutationConsumer {
        void accept(ChunkMutationContext context);
    }

    static final class ChunkMutationContext {
        private final ChunkPos chunkPos;
        private final CompoundTag chunkNbt;
        private final ListTag sections;
        private final ListTag blockEntities;
        private final Map<Integer, SectionData> sectionByY = new HashMap<>();

        ChunkMutationContext(ChunkPos chunkPos, CompoundTag chunkNbt) {
            this.chunkPos = chunkPos;
            this.chunkNbt = chunkNbt;

            ListTag existingSections = chunkNbt.getListOrEmpty("sections");
            this.sections = existingSections;
            if (!chunkNbt.contains("sections")) {
                chunkNbt.put("sections", this.sections);
            }

            ListTag existingBlockEntities = chunkNbt.getListOrEmpty("block_entities");
            this.blockEntities = existingBlockEntities;
            if (!chunkNbt.contains("block_entities")) {
                chunkNbt.put("block_entities", this.blockEntities);
            }
        }

        BlockSnapshot getSnapshot(int x, int y, int z) {
            int localX = x & 15;
            int localY = y & 15;
            int localZ = z & 15;
            int sectionY = Math.floorDiv(y, 16);

            SectionData section = getOrCreateSection(sectionY, false);
            CompoundTag stateNbt = section.getStateAt(localX, localY, localZ);
            String blockString = toBlockString(stateNbt);
            String nbtSnbt = extractBlockEntitySnbtAt(x, y, z);
            return new BlockSnapshot(blockString, nbtSnbt);
        }

        boolean setBlock(int x, int y, int z, String blockString, String blockEntitySnbt) {
            int localX = x & 15;
            int localY = y & 15;
            int localZ = z & 15;
            int sectionY = Math.floorDiv(y, 16);

            SectionData section = getOrCreateSection(sectionY, true);
            CompoundTag targetState = parseBlockString(blockString);
            boolean changed = section.setStateAt(localX, localY, localZ, targetState);

            String targetBlockName = targetState.getStringOr("Name", "minecraft:air");
            String filteredBlockEntitySnbt = blockSupportsBlockEntity(targetBlockName) ? blockEntitySnbt : null;
            boolean blockEntityChanged = setBlockEntityAt(x, y, z, filteredBlockEntitySnbt);
            return changed || blockEntityChanged;
        }

        void finish() {
            for (SectionData section : sectionByY.values()) {
                section.flush();
            }
            chunkNbt.put("sections", sections);
            chunkNbt.put("block_entities", blockEntities);
        }

        private SectionData getOrCreateSection(int sectionY, boolean createIfMissing) {
            SectionData cached = sectionByY.get(sectionY);
            if (cached != null) {
                if (createIfMissing && !cached.attachedToSectionsList) {
                    sections.add(cached.section);
                    cached.attachedToSectionsList = true;
                }
                return cached;
            }

            for (int i = 0; i < sections.size(); i++) {
                CompoundTag section = sections.getCompoundOrEmpty(i);
                if (section.getByteOr("Y", (byte) 0) == (byte) sectionY) {
                    SectionData data = new SectionData(section, true);
                    sectionByY.put(sectionY, data);
                    return data;
                }
            }

            CompoundTag created = createEmptySection(sectionY);
            if (createIfMissing) {
                sections.add(created);
            }
            SectionData data = new SectionData(created, createIfMissing);
            sectionByY.put(sectionY, data);
            return data;
        }

        private String extractBlockEntitySnbtAt(int x, int y, int z) {
            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag be = blockEntities.getCompoundOrEmpty(i);
                if (be.getIntOr("x", Integer.MIN_VALUE) == x
                    && be.getIntOr("y", Integer.MIN_VALUE) == y
                    && be.getIntOr("z", Integer.MIN_VALUE) == z) {
                    return NbtUtils.structureToSnbt(be);
                }
            }
            return null;
        }

        private boolean setBlockEntityAt(int x, int y, int z, String blockEntitySnbt) {
            boolean removed = false;
            for (int i = blockEntities.size() - 1; i >= 0; i--) {
                CompoundTag be = blockEntities.getCompoundOrEmpty(i);
                if (be.getIntOr("x", Integer.MIN_VALUE) == x
                    && be.getIntOr("y", Integer.MIN_VALUE) == y
                    && be.getIntOr("z", Integer.MIN_VALUE) == z) {
                    blockEntities.remove(i);
                    removed = true;
                }
            }

            if (isBlankSafe(blockEntitySnbt)) {
                return removed;
            }

            CompoundTag be;
            try {
                be = NbtUtils.snbtToStructure(blockEntitySnbt);
            } catch (Exception e) {
                return removed;
            }

            if (!be.contains("id") && be.contains("Id")) {
                String legacyId = be.getStringOr("Id", "");
                if (!isBlankSafe(legacyId)) {
                    be.putString("id", legacyId);
                }
            }

            String beId = be.getStringOr("id", "");
            if (isBlankSafe(beId)) {
                return removed;
            }

            be.putInt("x", x);
            be.putInt("y", y);
            be.putInt("z", z);
            blockEntities.add(be);
            return true;
        }
    }

    record BlockSnapshot(String blockString, String nbtSnbt) {
    }

    record BlockStateSpec(String blockString, String blockEntitySnbt) {
    }

    static Optional<CompoundTag> loadChunkNbt(ServerLevel world, ChunkPos chunkPos) {
        ChunkMap loadingManager = world.getChunkSource().chunkMap;
        Optional<CompoundTag> optional;
        try {
            optional = loadingManager.read(chunkPos)
                .orTimeout(CHUNK_NBT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Timed out loading chunk NBT for {}:{}", chunkPos.x(), chunkPos.z());
            return Optional.empty();
        }
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag chunkNbt = optional.get().copy();

        // ChunkMap now exposes already data-fixed NBT through SimpleRegionStorage;
        // the old ChunkStorage.updateChunkNbt hook was removed in 26.2.
        return Optional.of(chunkNbt);
    }

    static void saveChunkNbt(ServerLevel world, ChunkPos chunkPos, CompoundTag chunkNbt) {
        ChunkMap loadingManager = world.getChunkSource().chunkMap;
        loadingManager.write(chunkPos, () -> chunkNbt)
            .orTimeout(CHUNK_NBT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .join();
    }

    static boolean hasChunkNbt(ServerLevel world, ChunkPos chunkPos) {
        ChunkMap loadingManager = world.getChunkSource().chunkMap;
        try {
            return loadingManager.read(chunkPos)
                .orTimeout(CHUNK_NBT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join()
                .isPresent();
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Timed out probing chunk NBT for {}:{}", chunkPos.x(), chunkPos.z());
            return false;
        }
    }

    static boolean mutateChunk(ServerLevel world, ChunkPos chunkPos, MutationConsumer consumer) {
        if (world.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
            return false;
        }

        Optional<CompoundTag> optionalChunkNbt = loadChunkNbt(world, chunkPos);
        if (optionalChunkNbt.isEmpty()) {
            return false;
        }

        CompoundTag chunkNbt = optionalChunkNbt.get();
        ChunkMutationContext context = new ChunkMutationContext(chunkPos, chunkNbt);
        consumer.accept(context);
        context.finish();

        if (world.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
            return false;
        }

        try {
            saveChunkNbt(world, chunkPos, chunkNbt);
            return true;
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Timed out saving chunk NBT for {}:{}", chunkPos.x(), chunkPos.z());
            return false;
        }
    }

    static BlockStateSpec fromBlockString(String blockString, String blockEntitySnbt) {
        String normalized = normalizeBlockString(blockString);
        return new BlockStateSpec(normalized, blockEntitySnbt);
    }

    static String normalizeBlockString(String input) {
        if (isBlankSafe(input)) {
            return "minecraft:air";
        }

        try {
            String trimmed = trimSafe(input);
            int nbtStart = trimmed.indexOf('{');
            if (nbtStart >= 0) {
                trimmed = trimmed.substring(0, nbtStart);
            }

            int propsStart = trimmed.indexOf('[');
            if (propsStart < 0) {
                return isBlankSafe(trimmed) ? "minecraft:air" : trimmed;
            }

            int propsEnd = trimmed.lastIndexOf(']');
            if (propsEnd <= propsStart) {
                return trimmed;
            }

            String name = trimmed.substring(0, propsStart);
            String propsRaw = trimmed.substring(propsStart + 1, propsEnd);
            if (isBlankSafe(propsRaw)) {
                return isBlankSafe(name) ? "minecraft:air" : name;
            }

            String[] parts = propsRaw.split(",");
            Map<String, String> props = new LinkedHashMap<>();
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                props.put(trimSafe(kv[0]), trimSafe(kv[1]));
            }

            if (props.isEmpty()) {
                return isBlankSafe(name) ? "minecraft:air" : name;
            }

            List<String> keys = new ArrayList<>(props.keySet());
            keys.sort(String::compareTo);

            StringBuilder out = new StringBuilder(name).append('[');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                String key = keys.get(i);
                out.append(key).append('=').append(props.get(key));
            }
            out.append(']');
            return out.toString();
        } catch (Throwable t) {
            logMalformedBlockString(t);
            return "minecraft:air";
        }
    }

    static CompoundTag parseBlockString(String blockString) {
        String normalized = normalizeBlockString(blockString);
        String name = normalized;
        String propsRaw = null;

        int bracket = normalized.indexOf('[');
        if (bracket >= 0) {
            name = normalized.substring(0, bracket);
            int end = normalized.lastIndexOf(']');
            if (end > bracket) {
                propsRaw = normalized.substring(bracket + 1, end);
            }
        }

        CompoundTag out = new CompoundTag();
        out.putString("Name", isBlankSafe(name) ? "minecraft:air" : name);

        if (propsRaw != null && !isBlankSafe(propsRaw)) {
            CompoundTag props = new CompoundTag();
            for (String part : propsRaw.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                props.putString(trimSafe(kv[0]), trimSafe(kv[1]));
            }
            if (!props.isEmpty()) {
                out.put("Properties", props);
            }
        }

        return out;
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
            String key = keys.get(i);
            out.append(key).append('=').append(props.getStringOr(key, ""));
        }
        out.append(']');
        return out.toString();
    }

    private static CompoundTag createEmptySection(int sectionY) {
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) sectionY);

        CompoundTag blockStates = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(parseBlockString("minecraft:air"));
        blockStates.put("palette", palette);
        section.put("block_states", blockStates);
        return section;
    }

    private static boolean blockSupportsBlockEntity(String blockName) {
        if (isBlankSafe(blockName)) {
            return false;
        }

        Identifier id = Identifier.tryParse(blockName);
        if (id == null) {
            return false;
        }

        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) {
            return false;
        }

        return block.defaultBlockState().hasBlockEntity();
    }

    private static boolean isBlankSafe(String value) {
        if (value == null) {
            return true;
        }
        try {
            return value.isBlank();
        } catch (Throwable t) {
            return true;
        }
    }

    private static String trimSafe(String value) {
        if (value == null) {
            return "";
        }
        try {
            return value.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void logMalformedBlockString(Throwable t) {
        if (MALFORMED_BLOCK_STRING_LOGGED.compareAndSet(false, true)) {
            AtlantisMod.LOGGER.warn("Encountered malformed block string in chunk mutation; defaulting invalid entries to minecraft:air.", t);
        }
    }

    private static final class SectionData {
        private final CompoundTag section;
        private boolean attachedToSectionsList;
        private final CompoundTag blockStates;
        private final List<CompoundTag> palette = new ArrayList<>();
        private final Map<String, Integer> paletteIndexByKey = new HashMap<>();
        private final int[] states = new int[4096];
        private boolean dirty;

        SectionData(CompoundTag section, boolean attachedToSectionsList) {
            this.section = section;
            this.attachedToSectionsList = attachedToSectionsList;
            this.blockStates = section.getCompoundOrEmpty("block_states");
            if (!section.contains("block_states")) {
                section.put("block_states", blockStates);
            }

            ListTag paletteNbt = blockStates.getListOrEmpty("palette");
            if (paletteNbt.isEmpty()) {
                CompoundTag air = parseBlockString("minecraft:air");
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

        CompoundTag getStateAt(int localX, int localY, int localZ) {
            int index = linearIndex(localX, localY, localZ);
            int paletteIndex = states[index];
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                return parseBlockString("minecraft:air");
            }
            return palette.get(paletteIndex).copy();
        }

        boolean setStateAt(int localX, int localY, int localZ, CompoundTag stateNbt) {
            int index = linearIndex(localX, localY, localZ);
            int targetPaletteIndex = getOrCreatePaletteIndex(stateNbt);
            if (states[index] == targetPaletteIndex) {
                return false;
            }
            states[index] = targetPaletteIndex;
            dirty = true;
            return true;
        }

        void flush() {
            if (!dirty) {
                return;
            }

            ListTag paletteNbt = new ListTag();
            for (CompoundTag state : palette) {
                paletteNbt.add(state.copy());
            }
            blockStates.put("palette", paletteNbt);

            if (palette.size() <= 1) {
                blockStates.remove("data");
            } else {
                long[] data = encodeStates();
                blockStates.putLongArray("data", data);
            }

            section.put("block_states", blockStates);
            dirty = false;
        }

        private int getOrCreatePaletteIndex(CompoundTag stateNbt) {
            String key = toBlockString(stateNbt);
            Integer existing = paletteIndexByKey.get(key);
            if (existing != null) {
                return existing;
            }

            int index = palette.size();
            palette.add(stateNbt.copy());
            paletteIndexByKey.put(key, index);
            return index;
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
                int valueIndex = i % valuesPerLong;
                int bitOffset = valueIndex * bits;

                if (longIndex >= packed.length) {
                    states[i] = 0;
                    continue;
                }

                long value = packed[longIndex] >>> bitOffset;

                int paletteIndex = (int) (value & mask);
                states[i] = (paletteIndex >= 0 && paletteIndex < palette.size()) ? paletteIndex : 0;
            }
        }

        private long[] encodeStates() {
            int bits = Math.max(4, ceilLog2(palette.size()));
            int valuesPerLong = Math.max(1, 64 / bits);
            int longs = (states.length + valuesPerLong - 1) / valuesPerLong;
            long[] packed = new long[longs];
            long mask = (1L << bits) - 1L;

            for (int i = 0; i < states.length; i++) {
                long value = (long) states[i] & mask;
                int longIndex = i / valuesPerLong;
                int valueIndex = i % valuesPerLong;
                int bitOffset = valueIndex * bits;

                packed[longIndex] = (packed[longIndex] & ~(mask << bitOffset)) | (value << bitOffset);
            }

            return packed;
        }

        private static int linearIndex(int localX, int localY, int localZ) {
            return (localY << 8) | (localZ << 4) | localX;
        }

        private static int ceilLog2(int value) {
            if (value <= 1) {
                return 0;
            }
            return 32 - Integer.numberOfLeadingZeros(value - 1);
        }
    }
}
