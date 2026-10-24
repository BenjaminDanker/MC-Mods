package com.silver.branchreset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deletes the per-player files a server keeps about someone who is offline. */
final class WipeFiles {
    private WipeFiles() {
    }

    /** Returns the names of the files that existed and were removed. */
    static List<String> erase(UUID player, Path advancements, Path stats, Path playerData) throws IOException {
        List<String> removed = new ArrayList<>();
        delete(advancements.resolve(player + ".json"), removed);
        delete(stats.resolve(player + ".json"), removed);
        delete(playerData.resolve(player + ".dat"), removed);
        delete(playerData.resolve(player + ".dat_old"), removed);
        return removed;
    }

    private static void delete(Path file, List<String> removed) throws IOException {
        if (Files.deleteIfExists(file)) {
            removed.add(file.getFileName().toString());
        }
    }
}
