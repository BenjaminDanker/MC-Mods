package com.silver.atlantis.protect;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persists per-run protection sets to disk so protection survives restarts.
 */
public final class ProtectionFileIO {

    private static final int VERSION = 1;

    private ProtectionFileIO() {
    }

    public static void write(Path file, ProtectionEntry entry) throws IOException {
        if (file == null || entry == null) {
            return;
        }

        Files.createDirectories(file.getParent());

        try (var raw = Files.newOutputStream(file);
             var gzip = new GZIPOutputStream(new BufferedOutputStream(raw));
             var out = new DataOutputStream(gzip)) {

            out.writeInt(VERSION);
            writeString(out, entry.id());
            writeString(out, entry.dimensionId());

            writeLongSet(out, entry.placedPositions());
            writeLongSet(out, entry.interiorPositions());
        }
    }

    public static ProtectionEntry read(Path file) throws IOException {
        try (var raw = Files.newInputStream(file);
             var gzip = new GZIPInputStream(new BufferedInputStream(raw));
             var in = new DataInputStream(gzip)) {

            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported protection file version: " + version);
            }

            String id = readString(in);
            String dimensionId = readString(in);

            LongSet placed = readLongSet(in);
            LongSet interior = readLongSet(in);

            return new ProtectionEntry(id, dimensionId, placed, interior);
        }
    }

    private static void writeLongSet(DataOutputStream out, LongSet set) throws IOException {
        if (set == null || set.isEmpty()) {
            out.writeInt(0);
            return;
        }

        out.writeInt(set.size());
        var it = set.iterator();
        while (it.hasNext()) {
            out.writeLong(it.nextLong());
        }
    }

    private static LongSet readLongSet(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Invalid protection set count: " + count);
        }

        LongOpenHashSet set = new LongOpenHashSet(Math.max(16, count));
        for (int i = 0; i < count; i++) {
            set.add(in.readLong());
        }
        return set;
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        String safe = (s != null) ? s : "";
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 16 * 1024 * 1024) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
