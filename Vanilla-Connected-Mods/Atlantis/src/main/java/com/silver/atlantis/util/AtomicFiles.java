package com.silver.atlantis.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes files through a temporary sibling that is renamed over the target when complete, so a crash, a stop or a
 * concurrent reader never sees a half-written file: it sees the old contents or the new ones.
 */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    @FunctionalInterface
    public interface Body {
        void write(OutputStream out) throws IOException;
    }

    public static void write(Path file, Body body) throws IOException {
        try (OutputStream out = openReplacing(file)) {
            body.write(out);
        }
    }

    public static void writeString(Path file, String text) throws IOException {
        write(file, out -> out.write(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * A stream into a temporary file that replaces {@code file} when closed. For long streamed writes (undo files)
     * whatever was written up to the close is kept, which is what a partial run needs.
     */
    public static OutputStream openReplacing(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + file.getFileName() + ".", ".tmp");
        OutputStream raw = Files.newOutputStream(temporary);
        return new FilterOutputStream(raw) {
            private boolean closed;

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                try {
                    super.close();
                    try {
                        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        };
    }
}
