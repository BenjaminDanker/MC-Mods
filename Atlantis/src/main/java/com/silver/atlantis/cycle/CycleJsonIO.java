package com.silver.atlantis.cycle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CycleJsonIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CycleJsonIO() {
    }

    public static CycleState readOrCreateState(Path file) {
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    CycleState st = GSON.fromJson(reader, CycleState.class);
                    if (st != null && st.version() == CycleState.CURRENT_VERSION) {
                        return st;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        CycleState st = CycleState.disabled();
        try {
            write(file, st);
        } catch (Exception ignored) {
        }
        return st;
    }

    /**
     * Best-effort read; returns null when missing/invalid.
     * Does not create or overwrite any files.
     */
    public static CycleState tryRead(Path file) {
        try {
            if (file == null || !Files.exists(file)) {
                return null;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                CycleState st = GSON.fromJson(reader, CycleState.class);
                if (st != null && st.version() == CycleState.CURRENT_VERSION) {
                    return st;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void write(Path file, Object obj) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(obj, writer);
        }
    }
}
