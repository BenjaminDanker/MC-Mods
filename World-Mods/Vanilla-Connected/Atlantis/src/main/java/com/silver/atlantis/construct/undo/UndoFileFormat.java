package com.silver.atlantis.construct.undo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class UndoFileFormat {

    private static final int VERSION = 1;
    private static final int VERSION_STREAMING = 2;

    private UndoFileFormat() {
    }

    public static void write(Path file, String dimension, List<UndoEntry> entries) throws IOException {
        Files.createDirectories(file.getParent());

        Map<String, Integer> blockPalette = new HashMap<>();
        Map<String, Integer> nbtPalette = new HashMap<>();
        List<String> blocks = new ArrayList<>();
        List<String> nbts = new ArrayList<>();

        for (UndoEntry entry : entries) {
            blockPalette.computeIfAbsent(entry.blockString(), s -> {
                blocks.add(s);
                return blocks.size() - 1;
            });
            if (entry.nbtSnbt() != null) {
                nbtPalette.computeIfAbsent(entry.nbtSnbt(), s -> {
                    nbts.add(s);
                    return nbts.size() - 1;
                });
            }
        }

        try (var raw = com.silver.atlantis.util.AtomicFiles.openReplacing(file);
             var gzip = new GZIPOutputStream(new BufferedOutputStream(raw));
             var out = new DataOutputStream(gzip)) {

            out.writeInt(VERSION);
            writeString(out, dimension);

            out.writeInt(blocks.size());
            for (String s : blocks) {
                writeString(out, s);
            }

            out.writeInt(nbts.size());
            for (String s : nbts) {
                writeString(out, s);
            }

            out.writeInt(entries.size());
            for (UndoEntry entry : entries) {
                out.writeInt(entry.x());
                out.writeInt(entry.y());
                out.writeInt(entry.z());

                out.writeInt(blockPalette.get(entry.blockString()));
                if (entry.nbtSnbt() == null) {
                    out.writeInt(-1);
                } else {
                    out.writeInt(nbtPalette.get(entry.nbtSnbt()));
                }
            }
        }
    }

    public static UndoData read(Path file) throws IOException {
        try (var raw = Files.newInputStream(file);
             var gzip = new GZIPInputStream(new BufferedInputStream(raw));
             var in = new DataInputStream(gzip)) {

            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported undo file version: " + version);
            }

            String dimension = readString(in);

            int blocksCount = in.readInt();
            List<String> blocks = new ArrayList<>(blocksCount);
            for (int i = 0; i < blocksCount; i++) {
                blocks.add(readString(in));
            }

            int nbtsCount = in.readInt();
            List<String> nbts = new ArrayList<>(nbtsCount);
            for (int i = 0; i < nbtsCount; i++) {
                nbts.add(readString(in));
            }

            int entryCount = in.readInt();
            List<UndoEntry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                int blockIdx = in.readInt();
                int nbtIdx = in.readInt();

                String block = blocks.get(blockIdx);
                String nbt = (nbtIdx >= 0) ? nbts.get(nbtIdx) : null;
                entries.add(new UndoEntry(x, y, z, block, nbt));
            }

            return new UndoData(dimension, entries);
        }
    }

    /**
     * Read the undo file in a low-allocation form.
     */
    public static UndoDataIndexed readIndexed(Path file) throws IOException {
        try (var raw = Files.newInputStream(file);
             var gzip = new GZIPInputStream(new BufferedInputStream(raw));
             var in = new DataInputStream(gzip)) {

            int version = in.readInt();
            if (version == VERSION_STREAMING) {
                boolean[] truncated = new boolean[1];
                UndoDataIndexed data = readIndexedStreamingV2(in, truncated);
                if (truncated[0]) {
                    com.silver.atlantis.AtlantisMod.LOGGER.warn(
                        "[undo] {} ends mid-record (the build was interrupted); using the {} complete record(s) before that",
                        file, data.entryCount());
                }
                return data;
            }
            if (version != VERSION) {
                throw new IOException("Unsupported undo file version: " + version);
            }

            String dimension = readString(in);

            int blocksCount = in.readInt();
            String[] blocks = new String[blocksCount];
            for (int i = 0; i < blocksCount; i++) {
                blocks[i] = readString(in);
            }

            int nbtsCount = in.readInt();
            String[] nbts = new String[nbtsCount];
            for (int i = 0; i < nbtsCount; i++) {
                nbts[i] = readString(in);
            }

            int entryCount = in.readInt();
            int[] xs = new int[entryCount];
            int[] ys = new int[entryCount];
            int[] zs = new int[entryCount];
            int[] blockIdx = new int[entryCount];
            int[] nbtIdx = new int[entryCount];

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (int i = 0; i < entryCount; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();

                xs[i] = x;
                ys[i] = y;
                zs[i] = z;
                blockIdx[i] = in.readInt();
                nbtIdx[i] = in.readInt();

                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }

            if (entryCount == 0) {
                minX = minY = minZ = 0;
                maxX = maxY = maxZ = -1;
            }

            return new UndoDataIndexed(dimension, blocks, nbts, xs, ys, zs, blockIdx, nbtIdx, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /**
     * A new undo file. Written in place, not through a temporary file: records already written survive a crash, and
     * the reader keeps everything before a cut-off.
     */
    public static StreamWriter openStreamWriter(Path file, String dimension) throws IOException {
        Files.createDirectories(file.getParent());
        var raw = Files.newOutputStream(file);
        var gzip = new GZIPOutputStream(new BufferedOutputStream(raw));
        var out = new DataOutputStream(gzip);
        out.writeInt(VERSION_STREAMING);
        writeString(out, dimension);
        return new StreamWriter(out, null);
    }

    /**
     * Continues the undo file of an earlier, unfinished attempt of the same run. Its records are kept (the file is
     * rewritten cleanly first if it was cut off), new records go into an appended gzip member, and positions that
     * already have a record are skipped, so the block from before the first attempt is the one an undo restores.
     */
    public static StreamWriter openStreamWriterContinuing(Path file, String dimension) throws IOException {
        TolerantRead existing = readStreamingTolerant(file);
        if (existing.truncated()) {
            rewrite(file, existing.data());
        }
        it.unimi.dsi.fastutil.longs.LongOpenHashSet recorded =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet(Math.max(16, existing.data().entryCount()));
        UndoDataIndexed data = existing.data();
        for (int i = 0; i < data.entryCount(); i++) {
            recorded.add(net.minecraft.core.BlockPos.asLong(data.xs()[i], data.ys()[i], data.zs()[i]));
        }
        var raw = Files.newOutputStream(file, java.nio.file.StandardOpenOption.APPEND);
        var out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(raw)));
        return new StreamWriter(out, recorded);
    }

    private record TolerantRead(UndoDataIndexed data, boolean truncated) {
    }

    private static TolerantRead readStreamingTolerant(Path file) throws IOException {
        try (var raw = Files.newInputStream(file);
             var gzip = new GZIPInputStream(new BufferedInputStream(raw));
             var in = new DataInputStream(gzip)) {
            int version = in.readInt();
            if (version != VERSION_STREAMING) {
                throw new IOException("Cannot continue undo file of version " + version + ": " + file);
            }
            boolean[] truncated = new boolean[1];
            UndoDataIndexed data = readIndexedStreamingV2(in, truncated);
            return new TolerantRead(data, truncated[0]);
        }
    }

    /** Rewrites the records of a cut-off file as a clean, complete file (temporary file, then rename). */
    private static void rewrite(Path file, UndoDataIndexed data) throws IOException {
        try (var raw = com.silver.atlantis.util.AtomicFiles.openReplacing(file);
             var out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(raw)))) {
            out.writeInt(VERSION_STREAMING);
            writeString(out, data.dimension());
            StreamWriter writer = new StreamWriter(out, null);
            for (int i = 0; i < data.entryCount(); i++) {
                int nbt = data.nbtIdx()[i];
                writer.write(new UndoEntry(data.xs()[i], data.ys()[i], data.zs()[i], data.blocks()[data.blockIdx()[i]],
                    nbt < 0 ? null : data.nbts()[nbt]));
            }
        }
    }

    private static UndoDataIndexed readIndexedStreamingV2(DataInputStream in, boolean[] truncated) throws IOException {
        String dimension = readString(in);

        int capacity = 8_192;
        int size = 0;
        int[] xs = new int[capacity];
        int[] ys = new int[capacity];
        int[] zs = new int[capacity];
        int[] blockIdx = new int[capacity];
        int[] nbtIdx = new int[capacity];

        Map<String, Integer> blockPalette = new HashMap<>();
        Map<String, Integer> nbtPalette = new HashMap<>();
        List<String> blocks = new ArrayList<>();
        List<String> nbts = new ArrayList<>();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        while (true) {
            int x;
            try {
                x = in.readInt();
            } catch (java.io.EOFException eof) {
                // A clean end of file, or a file cut off exactly between records (an interrupted write).
                truncated[0] = eof.getMessage() != null;
                break;
            } catch (java.util.zip.ZipException corrupt) {
                truncated[0] = true;
                break;
            }

            int y;
            int z;
            String block;
            String nbt;
            try {
                y = in.readInt();
                z = in.readInt();
                block = readString(in);
                boolean hasNbt = in.readBoolean();
                nbt = hasNbt ? readString(in) : null;
            } catch (java.io.EOFException | java.util.zip.ZipException cutOff) {
                truncated[0] = true;
                break;
            }

            if (size >= capacity) {
                capacity *= 2;
                xs = java.util.Arrays.copyOf(xs, capacity);
                ys = java.util.Arrays.copyOf(ys, capacity);
                zs = java.util.Arrays.copyOf(zs, capacity);
                blockIdx = java.util.Arrays.copyOf(blockIdx, capacity);
                nbtIdx = java.util.Arrays.copyOf(nbtIdx, capacity);
            }

            xs[size] = x;
            ys[size] = y;
            zs[size] = z;
            blockIdx[size] = blockPalette.computeIfAbsent(block, s -> {
                blocks.add(s);
                return blocks.size() - 1;
            });
            nbtIdx[size] = (nbt == null) ? -1 : nbtPalette.computeIfAbsent(nbt, s -> {
                nbts.add(s);
                return nbts.size() - 1;
            });

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;

            size++;
        }

        if (size == 0) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = -1;
        }

        return new UndoDataIndexed(
            dimension,
            blocks.toArray(String[]::new),
            nbts.toArray(String[]::new),
            java.util.Arrays.copyOf(xs, size),
            java.util.Arrays.copyOf(ys, size),
            java.util.Arrays.copyOf(zs, size),
            java.util.Arrays.copyOf(blockIdx, size),
            java.util.Arrays.copyOf(nbtIdx, size),
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ
        );
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 64 * 1024 * 1024) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class StreamWriter implements AutoCloseable {
        private final DataOutputStream out;
        /** Positions an earlier attempt already recorded; their first record is the one to keep. Null for a new file. */
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet recorded;
        private int entryCount;

        private StreamWriter(DataOutputStream out, it.unimi.dsi.fastutil.longs.LongOpenHashSet recorded) {
            this.out = out;
            this.recorded = recorded;
        }

        public void write(UndoEntry entry) {
            if (recorded != null && !recorded.add(net.minecraft.core.BlockPos.asLong(entry.x(), entry.y(), entry.z()))) {
                return;
            }
            try {
                out.writeInt(entry.x());
                out.writeInt(entry.y());
                out.writeInt(entry.z());
                writeString(out, entry.blockString());
                boolean hasNbt = entry.nbtSnbt() != null;
                out.writeBoolean(hasNbt);
                if (hasNbt) {
                    writeString(out, entry.nbtSnbt());
                }
                entryCount++;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to stream undo entry", e);
            }
        }

        public int entryCount() {
            return entryCount;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    public record UndoData(String dimension, List<UndoEntry> entries) {
    }

    public record UndoDataIndexed(
        String dimension,
        String[] blocks,
        String[] nbts,
        int[] xs,
        int[] ys,
        int[] zs,
        int[] blockIdx,
        int[] nbtIdx,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {

        public int entryCount() {
            return xs.length;
        }
    }
}
