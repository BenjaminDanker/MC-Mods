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

        try (var raw = Files.newOutputStream(file);
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
                return readIndexedStreamingV2(in);
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

    public static StreamWriter openStreamWriter(Path file, String dimension) throws IOException {
        Files.createDirectories(file.getParent());
        var raw = Files.newOutputStream(file);
        var gzip = new GZIPOutputStream(new BufferedOutputStream(raw));
        var out = new DataOutputStream(gzip);
        out.writeInt(VERSION_STREAMING);
        writeString(out, dimension);
        return new StreamWriter(out);
    }

    private static UndoDataIndexed readIndexedStreamingV2(DataInputStream in) throws IOException {
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
                break;
            }

            int y = in.readInt();
            int z = in.readInt();
            String block = readString(in);
            boolean hasNbt = in.readBoolean();
            String nbt = hasNbt ? readString(in) : null;

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
        private int entryCount;

        private StreamWriter(DataOutputStream out) {
            this.out = out;
        }

        public void write(UndoEntry entry) {
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
