package com.silver.atlantis.protect;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.undo.UndoPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Loads persisted protection data from previous construct runs.
 */
public final class ProtectionBootstrap {

    private ProtectionBootstrap() {
    }

    public static void loadAllPersisted() {
        Path base = UndoPaths.undoBaseDir();
        if (!Files.exists(base) || !Files.isDirectory(base)) {
            return;
        }

        int loaded = 0;
        int failed = 0;

        try (Stream<Path> children = Files.list(base)) {
            for (Path runDir : children.filter(Files::isDirectory).toList()) {
                Path file = ProtectionPaths.protectionFile(runDir);
                if (!Files.exists(file)) {
                    continue;
                }

                try {
                    ProtectionEntry entry = ProtectionFileIO.read(file);
                    if (entry != null && entry.id() != null && !entry.id().isBlank() && entry.dimensionId() != null && !entry.dimensionId().isBlank()) {
                        ProtectionManager.INSTANCE.register(entry);
                        loaded++;
                    }
                } catch (Exception e) {
                    failed++;
                    AtlantisMod.LOGGER.warn(String.format(Locale.ROOT,
                        "Failed to load protection file %s: %s",
                        file,
                        e.getMessage()
                    ));
                }
            }
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to scan undo directory for protection files: {}", e.getMessage());
            return;
        }

        if (loaded > 0 || failed > 0) {
            AtlantisMod.LOGGER.info(String.format(Locale.ROOT,
                "Loaded %d persisted protection run(s) (%d failed).",
                loaded,
                failed
            ));
        }
    }
}
