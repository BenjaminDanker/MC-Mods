package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.undo.UndoEntry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SpongeV3Schematic {
    record PlacementBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    record PrepassResult(Set<Long> targetChunkKeys, PlacementBounds bounds) {
    }

    record StreamApplyResult(Set<Long> placedKeys, Set<Long> interiorKeys, int chunkCount, int writeCount, int undoEntryCount) {
    }

    interface UndoEntrySink {
        void accept(UndoEntry entry);
    }

    private final int width;
    private final int height;
    private final int length;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final String[] paletteById;
    private final byte[] data;
    private final Map<Long, String> blockEntitySnbtBySchematicPos;

    private SpongeV3Schematic(
        int width,
        int height,
        int length,
        int offsetX,
        int offsetY,
        int offsetZ,
        String[] paletteById,
        byte[] data,
        Map<Long, String> blockEntitySnbtBySchematicPos
    ) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.paletteById = paletteById;
        this.data = data;
        this.blockEntitySnbtBySchematicPos = blockEntitySnbtBySchematicPos;
    }

    static SpongeV3Schematic load(Path path) throws IOException {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("Schematic not found: " + path);
        }

        NbtCompound root = NbtIo.readCompressed(path, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound schematic = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;

        int version = schematic.getInt("Version", -1);
        if (version != 3) {
            throw new IOException("Unsupported schematic version: " + version + " (expected Sponge v3)");
        }

        int width = schematic.getShort("Width", (short) 0);
        int height = schematic.getShort("Height", (short) 0);
        int length = schematic.getShort("Length", (short) 0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Invalid schematic dimensions: " + width + "x" + height + "x" + length);
        }

        int offsetX = 0;
        int offsetY = 0;
        int offsetZ = 0;
        int[] offset = schematic.getIntArray("Offset").orElse(null);
        if (offset != null && offset.length >= 3) {
            offsetX = offset[0];
            offsetY = offset[1];
            offsetZ = offset[2];
        }

        NbtCompound blocks = schematic.getCompoundOrEmpty("Blocks");
        NbtCompound palette = blocks.getCompoundOrEmpty("Palette");
        byte[] data = blocks.getByteArray("Data").orElse(new byte[0]);
        if (palette.isEmpty() || data.length == 0) {
            throw new IOException("Invalid schematic: missing Blocks.Palette or Blocks.Data");
        }

        int maxPaletteId = 0;
        for (String key : palette.getKeys()) {
            maxPaletteId = Math.max(maxPaletteId, palette.getInt(key, 0));
        }
        String[] paletteById = new String[maxPaletteId + 1];
        for (String blockState : palette.getKeys()) {
            int id = palette.getInt(blockState, -1);
            if (id >= 0 && id < paletteById.length) {
                paletteById[id] = blockState;
            }
        }

        Map<Long, String> blockEntities = new HashMap<>();
        NbtList blockEntityList = blocks.getListOrEmpty("BlockEntities");
        for (int i = 0; i < blockEntityList.size(); i++) {
            NbtCompound be = blockEntityList.getCompoundOrEmpty(i);
            int[] pos = be.getIntArray("Pos").orElse(new int[0]);
            if (pos.length < 3) {
                continue;
            }

            int schematicX = offsetX + pos[0];
            int schematicY = offsetY + pos[1];
            int schematicZ = offsetZ + pos[2];
            long key = BlockPos.asLong(schematicX, schematicY, schematicZ);
            blockEntities.put(key, NbtHelper.toNbtProviderString(be));
        }

        int expectedBlocks = width * height * length;
        int decodedCount = countVarints(data, expectedBlocks);
        if (decodedCount != expectedBlocks) {
            throw new IOException("Invalid schematic data length: decoded=" + decodedCount + " expected=" + expectedBlocks);
        }

        return new SpongeV3Schematic(width, height, length, offsetX, offsetY, offsetZ, paletteById, data, blockEntities);
    }

    BlockPos computeCenteredAnchor(BlockPos targetCenter) {
        int minX = offsetX;
        int maxX = offsetX + width - 1;
        int minZ = offsetZ;
        int maxZ = offsetZ + length - 1;

        int centerX = (int) Math.floor((minX + maxX) / 2.0);
        int centerZ = (int) Math.floor((minZ + maxZ) / 2.0);

        int toX = targetCenter.getX() - centerX;
        int toY = targetCenter.getY();
        int toZ = targetCenter.getZ() - centerZ;
        return new BlockPos(toX, toY, toZ);
    }

    PlacementBounds computePlacementBounds(BlockPos anchorTo) {
        int minX = offsetX + anchorTo.getX();
        int minY = offsetY + anchorTo.getY();
        int minZ = offsetZ + anchorTo.getZ();
        int maxX = offsetX + width - 1 + anchorTo.getX();
        int maxY = offsetY + height - 1 + anchorTo.getY();
        int maxZ = offsetZ + length - 1 + anchorTo.getZ();
        return new PlacementBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    PrepassResult runPrepass(BlockPos anchorTo) {
        Set<Long> targetChunks = new HashSet<>();
        PlacementBounds bounds = computePlacementBounds(anchorTo);

        VarIntCursor cursor = new VarIntCursor(data);
        int area = width * length;
        int total = area * height;
        for (int index = 0; index < total; index++) {
            int paletteId = nextOrThrow(cursor);
            String block = paletteId >= 0 && paletteId < paletteById.length && paletteById[paletteId] != null
                ? paletteById[paletteId]
                : "minecraft:air";

            String normalized = UnloadedChunkNbtEditor.normalizeBlockString(block);
            if (isAirLike(normalized)) {
                continue;
            }

            int y = index / area;
            int rem = index % area;
            int z = rem / width;
            int x = rem % width;

            int worldX = offsetX + x + anchorTo.getX();
            int worldZ = offsetZ + z + anchorTo.getZ();
            targetChunks.add(ChunkPos.toLong(worldX >> 4, worldZ >> 4));
        }

        return new PrepassResult(Collections.unmodifiableSet(new HashSet<>(targetChunks)), bounds);
    }

    StreamApplyResult streamApply(ServerWorld world, BlockPos anchorTo, UndoEntrySink undoEntrySink) {
        LongOpenHashSet touchedChunks = new LongOpenHashSet();
        LongOpenHashSet placedPositions = new LongOpenHashSet();
        LongOpenHashSet interiorPositions = new LongOpenHashSet();
        int[] writeCount = new int[1];
        int[] undoEntryCount = new int[1];
        long applyStartedAt = System.nanoTime();

        VarIntCursor cursor = new VarIntCursor(data);
        int area = width * length;
        int total = area * height;
        Path spoolDir = null;
        Map<Long, Path> spoolFiles = new HashMap<>();
        Map<Long, java.io.DataOutputStream> spoolOutputs = new HashMap<>();

        try {
            spoolDir = Files.createTempDirectory("atlantis-schem-spool-");

            for (int index = 0; index < total; index++) {
                int y = index / area;
                int rem = index % area;
                int z = rem / width;
                int x = rem % width;

                int schematicX = offsetX + x;
                int schematicY = offsetY + y;
                int schematicZ = offsetZ + z;

                int paletteId = nextOrThrow(cursor);
                String raw = paletteId >= 0 && paletteId < paletteById.length && paletteById[paletteId] != null
                    ? paletteById[paletteId]
                    : "minecraft:air";
                String normalized = UnloadedChunkNbtEditor.normalizeBlockString(raw);

                int worldX = schematicX + anchorTo.getX();
                int worldY = schematicY + anchorTo.getY();
                int worldZ = schematicZ + anchorTo.getZ();

                if (isInteriorAirMarker(normalized)) {
                    interiorPositions.add(BlockPos.asLong(worldX, worldY, worldZ));
                    long chunkKey = ChunkPos.toLong(worldX >> 4, worldZ >> 4);
                    appendPlacement(spoolDir, spoolFiles, spoolOutputs, chunkKey, worldX, worldY, worldZ, "minecraft:air", null);
                    continue;
                }

                if (isAirLike(normalized)) {
                    continue;
                }

                long schematicPosKey = BlockPos.asLong(schematicX, schematicY, schematicZ);
                String blockEntitySnbt = blockEntitySnbtBySchematicPos.get(schematicPosKey);
                long chunkKey = ChunkPos.toLong(worldX >> 4, worldZ >> 4);
                appendPlacement(spoolDir, spoolFiles, spoolOutputs, chunkKey, worldX, worldY, worldZ, normalized, blockEntitySnbt);
            }

            closeSpoolOutputs(spoolOutputs);

            int processedChunks = 0;
            int totalChunks = spoolFiles.size();
            for (Map.Entry<Long, Path> entry : spoolFiles.entrySet()) {
                long chunkKey = entry.getKey();
                int chunkX = ChunkPos.getPackedX(chunkKey);
                int chunkZ = ChunkPos.getPackedZ(chunkKey);
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

                boolean mutated = UnloadedChunkNbtEditor.mutateChunk(world, chunkPos, context -> {
                    try (var raw = Files.newInputStream(entry.getValue());
                         var in = new java.io.DataInputStream(new java.io.BufferedInputStream(raw))) {

                        while (true) {
                            int x;
                            try {
                                x = in.readInt();
                            } catch (java.io.EOFException eof) {
                                break;
                            }
                            int y = in.readInt();
                            int z = in.readInt();
                            String blockString = readString(in);
                            boolean hasNbt = in.readBoolean();
                            String blockEntitySnbt = hasNbt ? readString(in) : null;

                            var before = context.getSnapshot(x, y, z);
                            String beforeBlock = UnloadedChunkNbtEditor.normalizeBlockString(before.blockString());
                            String afterBlock = UnloadedChunkNbtEditor.normalizeBlockString(blockString);
                            String beforeNbt = normalizeNullable(before.nbtSnbt());
                            String afterNbt = normalizeNullable(blockEntitySnbt);

                            if (Objects.equals(beforeBlock, afterBlock) && Objects.equals(beforeNbt, afterNbt)) {
                                continue;
                            }

                            context.setBlock(x, y, z, blockString, blockEntitySnbt);
                            if (!isAirLike(afterBlock)) {
                                placedPositions.add(BlockPos.asLong(x, y, z));
                            }
                            writeCount[0]++;
                            undoEntrySink.accept(new UndoEntry(x, y, z, beforeBlock, beforeNbt));
                            undoEntryCount[0]++;
                        }
                    } catch (IOException io) {
                        throw new IllegalStateException("Failed reading chunk spool", io);
                    }
                });

                if (!mutated) {
                    throw new IllegalStateException("Target chunk unavailable for unloaded mutate (loaded or missing NBT): " + chunkPos.x + "," + chunkPos.z);
                }

                touchedChunks.add(chunkKey);
                processedChunks++;
                if (processedChunks % 50 == 0 || processedChunks == totalChunks) {
                    long elapsedMs = (System.nanoTime() - applyStartedAt) / 1_000_000L;
                    AtlantisMod.LOGGER.info("[construct] apply progress: chunks={}/{} writes={} undoEntries={} elapsed={}ms",
                        processedChunks,
                        totalChunks,
                        writeCount[0],
                        undoEntryCount[0],
                        elapsedMs
                    );
                }
            }
        } catch (IOException io) {
            throw new IllegalStateException("Schematic stream pass failed", io);
        } finally {
            closeSpoolOutputs(spoolOutputs);
            if (spoolDir != null) {
                deleteRecursively(spoolDir);
            }
        }

        return new StreamApplyResult(
            toJavaSet(placedPositions),
            toJavaSet(interiorPositions),
            touchedChunks.size(),
            writeCount[0],
            undoEntryCount[0]
        );
    }

    private static Set<Long> toJavaSet(LongOpenHashSet set) {
        if (set == null || set.isEmpty()) {
            return Set.of();
        }

        Set<Long> out = new HashSet<>(set.size());
        var iterator = set.iterator();
        while (iterator.hasNext()) {
            out.add(iterator.nextLong());
        }
        return Collections.unmodifiableSet(out);
    }

    private static void appendPlacement(
        Path spoolDir,
        Map<Long, Path> spoolFiles,
        Map<Long, java.io.DataOutputStream> spoolOutputs,
        long chunkKey,
        int x,
        int y,
        int z,
        String blockString,
        String blockEntitySnbt
    ) throws IOException {
        java.io.DataOutputStream out = spoolOutputs.get(chunkKey);
        if (out == null) {
            Path file = spoolFiles.computeIfAbsent(chunkKey, key -> spoolDir.resolve("chunk_" + ChunkPos.getPackedX(key) + "_" + ChunkPos.getPackedZ(key) + ".bin"));
            out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)));
            spoolOutputs.put(chunkKey, out);
        }

        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        writeString(out, blockString);
        boolean hasNbt = blockEntitySnbt != null;
        out.writeBoolean(hasNbt);
        if (hasNbt) {
            writeString(out, blockEntitySnbt);
        }
    }

    private static void closeSpoolOutputs(Map<Long, java.io.DataOutputStream> spoolOutputs) {
        for (java.io.DataOutputStream out : spoolOutputs.values()) {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
        spoolOutputs.clear();
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void writeString(java.io.DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(java.io.DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 64 * 1024 * 1024) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean isAirLike(String blockId) {
        if (blockId == null) {
            return true;
        }

        String base = blockNameOf(blockId);
        return "minecraft:air".equals(base)
            || "air".equals(base)
            || "minecraft:cave_air".equals(base)
            || "cave_air".equals(base)
            || "minecraft:void_air".equals(base)
            || "void_air".equals(base);
    }

    private static boolean isInteriorAirMarker(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }

        String base = blockNameOf(blockId);
        return "minecraft:barrier".equals(base)
            || "barrier".equals(base)
            || "minecraft:structure_void".equals(base)
            || "structure_void".equals(base);
    }

    private static String blockNameOf(String blockId) {
        String normalized = blockId.trim().toLowerCase(Locale.ROOT);

        int nbtStart = normalized.indexOf('{');
        if (nbtStart >= 0) {
            normalized = normalized.substring(0, nbtStart);
        }

        int propsStart = normalized.indexOf('[');
        if (propsStart >= 0) {
            normalized = normalized.substring(0, propsStart);
        }

        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static int countVarints(byte[] bytes, int hardLimit) throws IOException {
        VarIntCursor cursor = new VarIntCursor(bytes);
        int count = 0;
        while (cursor.hasRemaining() && count < hardLimit) {
            cursor.next();
            count++;
        }
        return count;
    }

    private static int nextOrThrow(VarIntCursor cursor) {
        try {
            return cursor.next();
        } catch (IOException e) {
            throw new IllegalStateException("Invalid schematic varint stream", e);
        }
    }

    private static final class VarIntCursor {
        private final byte[] bytes;
        private int index;

        private VarIntCursor(byte[] bytes) {
            this.bytes = bytes;
        }

        private boolean hasRemaining() {
            return index < bytes.length;
        }

        private int next() throws IOException {
            int value = 0;
            int position = 0;

            while (true) {
                if (index >= bytes.length) {
                    throw new IOException("Unexpected end of schematic varint stream");
                }
                int current = bytes[index++] & 0xFF;

                value |= (current & 0x7F) << position;
                if ((current & 0x80) == 0) {
                    return value;
                }

                position += 7;
                if (position >= 32) {
                    throw new IOException("Schematic varint too large");
                }
            }
        }
    }

}
