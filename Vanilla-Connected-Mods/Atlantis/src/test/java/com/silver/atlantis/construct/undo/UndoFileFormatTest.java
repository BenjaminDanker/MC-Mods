package com.silver.atlantis.construct.undo;

import com.silver.atlantis.construct.undo.UndoFileFormat.StreamWriter;
import com.silver.atlantis.construct.undo.UndoFileFormat.UndoDataIndexed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoFileFormatTest {

    private static final String DIMENSION = "minecraft:overworld";
    private static final String[] BLOCKS = {
        "minecraft:stone",
        "minecraft:dirt",
        "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]",
    };

    @TempDir
    Path dir;

    /** Unique position per index, so no two generated entries share a coordinate. */
    private static UndoEntry entry(int i) {
        String block = BLOCKS[i % BLOCKS.length];
        String nbt = (i % 4 == 0) ? "{id:\"minecraft:chest\",Slot:" + (i % 27) + "b}" : null;
        return new UndoEntry(i, 64 + (i % 5), -i * 2, block, nbt);
    }

    private static List<UndoEntry> entries(int from, int count) {
        List<UndoEntry> list = new ArrayList<>();
        for (int i = from; i < from + count; i++) {
            list.add(entry(i));
        }
        return list;
    }

    private static UndoEntry entryAt(UndoDataIndexed data, int i) {
        int nbt = data.nbtIdx()[i];
        return new UndoEntry(
            data.xs()[i],
            data.ys()[i],
            data.zs()[i],
            data.blocks()[data.blockIdx()[i]],
            nbt < 0 ? null : data.nbts()[nbt]
        );
    }

    private static void writeAll(Path file, List<UndoEntry> entries) throws Exception {
        try (StreamWriter writer = UndoFileFormat.openStreamWriter(file, DIMENSION)) {
            for (UndoEntry e : entries) {
                writer.write(e);
            }
        }
    }

    private static void truncateToFraction(Path file, double fraction) throws Exception {
        byte[] all = Files.readAllBytes(file);
        int keep = (int) (all.length * fraction);
        Files.write(file, Arrays.copyOf(all, keep));
    }

    @Test
    void streamedEntriesReadBackIdentically() throws Exception {
        Path file = dir.resolve("undo.dat");
        List<UndoEntry> expected = entries(0, 50);

        writeAll(file, expected);
        UndoDataIndexed data = UndoFileFormat.readIndexed(file);

        assertEquals(DIMENSION, data.dimension());
        assertEquals(expected.size(), data.entryCount());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), entryAt(data, i), "entry " + i);
        }
    }

    @Test
    void truncatedFileReadsWithoutThrowingAndKeepsOnlyCompleteOriginals() throws Exception {
        Path file = dir.resolve("truncated.dat");
        List<UndoEntry> originals = entries(0, 1000);
        writeAll(file, originals);

        truncateToFraction(file, 0.6);
        UndoDataIndexed data = UndoFileFormat.readIndexed(file);

        int count = data.entryCount();
        assertTrue(count >= 1 && count <= 999, "entry count was " + count);
        for (int i = 0; i < count; i++) {
            assertEquals(originals.get(i), entryAt(data, i), "entry " + i);
        }
    }

    @Test
    void continuingKeepsFirstRecordForDuplicatePositions() throws Exception {
        Path file = dir.resolve("continue-dup.dat");
        UndoEntry a = new UndoEntry(1, 2, 3, "minecraft:stone", null);
        UndoEntry b = new UndoEntry(4, 5, 6, "minecraft:stone", null);
        writeAll(file, List.of(a, b));

        UndoEntry bAgain = new UndoEntry(4, 5, 6, "minecraft:dirt", null);
        UndoEntry c = new UndoEntry(7, 8, 9, "minecraft:dirt", null);
        try (StreamWriter writer = UndoFileFormat.openStreamWriterContinuing(file, DIMENSION)) {
            writer.write(bAgain);
            writer.write(c);
        }

        UndoDataIndexed data = UndoFileFormat.readIndexed(file);

        assertEquals(3, data.entryCount());
        assertEquals(a, entryAt(data, 0));
        assertEquals(b, entryAt(data, 1));
        assertEquals("minecraft:stone", entryAt(data, 1).blockString());
        assertEquals(c, entryAt(data, 2));
    }

    @Test
    void continuingAfterTruncationKeepsSurvivorsThenAppendsNewEntries() throws Exception {
        Path file = dir.resolve("continue-truncated.dat");
        List<UndoEntry> originals = entries(0, 1000);
        writeAll(file, originals);
        truncateToFraction(file, 0.6);

        List<UndoEntry> fresh = entries(5000, 3);
        try (StreamWriter writer = UndoFileFormat.openStreamWriterContinuing(file, DIMENSION)) {
            for (UndoEntry e : fresh) {
                writer.write(e);
            }
        }

        UndoDataIndexed data = UndoFileFormat.readIndexed(file);

        int total = data.entryCount();
        int survivors = total - fresh.size();
        assertTrue(survivors >= 1 && survivors <= 999, "surviving originals were " + survivors);
        for (int i = 0; i < survivors; i++) {
            assertEquals(originals.get(i), entryAt(data, i), "original " + i);
        }
        for (int k = 0; k < fresh.size(); k++) {
            assertEquals(fresh.get(k), entryAt(data, survivors + k), "new entry " + k);
        }
    }

    @Test
    void fileWithNoEntriesReadsBackAsZero() throws Exception {
        Path file = dir.resolve("empty.dat");

        try (StreamWriter writer = UndoFileFormat.openStreamWriter(file, DIMENSION)) {
            assertEquals(0, writer.entryCount());
        }

        UndoDataIndexed data = UndoFileFormat.readIndexed(file);
        assertEquals(0, data.entryCount());
        assertEquals(DIMENSION, data.dimension());
    }
}
