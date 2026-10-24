package com.silver.atlantis.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {

    @TempDir
    Path dir;

    @Test
    void writeStringCreatesParentDirectoriesAndWritesText() throws Exception {
        Path file = dir.resolve("nested/deeper/out.txt");

        AtomicFiles.writeString(file, "héllo ☃ world");

        assertTrue(Files.isRegularFile(file));
        assertEquals("héllo ☃ world", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void writeStringReplacesExistingFile() throws Exception {
        Path file = dir.resolve("replace.txt");
        Files.writeString(file, "old contents that are longer than the new ones");

        AtomicFiles.writeString(file, "new");

        assertEquals("new", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void writeStringLeavesNoTmpFilesBehind() throws Exception {
        Path sub = dir.resolve("clean");
        Path file = sub.resolve("target.txt");

        AtomicFiles.writeString(file, "first");
        AtomicFiles.writeString(file, "second");

        try (Stream<Path> files = Files.list(sub)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of("target.txt"), names);
            assertFalse(names.stream().anyMatch(n -> n.endsWith(".tmp")));
        }
    }

    @Test
    void openReplacingKeepsOldContentsUntilClosed() throws Exception {
        Path file = dir.resolve("streamed.bin");
        Files.write(file, "OLD".getBytes(StandardCharsets.UTF_8));

        OutputStream out = AtomicFiles.openReplacing(file);
        out.write("NEW CONTENTS".getBytes(StandardCharsets.UTF_8));
        out.flush();

        assertEquals("OLD", Files.readString(file, StandardCharsets.UTF_8));

        out.close();

        assertEquals("NEW CONTENTS", Files.readString(file, StandardCharsets.UTF_8));
    }
}
