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
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                CycleState st = GSON.fromJson(reader, CycleState.class);
                if (st != null && st.version() == CycleState.CURRENT_VERSION) {
                    return st;
                }
                com.silver.atlantis.AtlantisMod.LOGGER.error("[cycle] ALERT: {} is empty or has an unknown version; "
                    + "the cycle stays disabled and the file is left as it is", file);
            } catch (Exception e) {
                com.silver.atlantis.AtlantisMod.LOGGER.error("[cycle] ALERT: cannot read {}; the cycle stays disabled and "
                    + "the file is left as it is (fix or delete it, then /atlantis cycle)", file, e);
            }
            return CycleState.disabled();
        }

        CycleState st = CycleState.disabled();
        try {
            write(file, st);
        } catch (Exception e) {
            com.silver.atlantis.AtlantisMod.LOGGER.error("[cycle] Could not create {}", file, e);
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
        } catch (Exception e) {
            com.silver.atlantis.AtlantisMod.LOGGER.warn("[cycle] Could not read {}: {}", file, e.toString());
        }
        return null;
    }

    public static void write(Path file, Object obj) throws IOException {
        com.silver.atlantis.util.AtomicFiles.writeString(file, GSON.toJson(obj));
    }
}
