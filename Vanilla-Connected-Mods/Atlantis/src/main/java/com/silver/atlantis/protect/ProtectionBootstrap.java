package com.silver.atlantis.protect;

import com.silver.atlantis.AtlantisMod;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the one persisted active protection object.
 */
public final class ProtectionBootstrap {

    private ProtectionBootstrap() {
    }

    public static void loadActivePersisted() {
        Path file = ProtectionPaths.activeProtectionFile();
        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            ProtectionEntry entry = ProtectionFileIO.read(file);
            if (entry != null && entry.id() != null && !entry.id().isBlank()
                && entry.dimensionId() != null && !entry.dimensionId().isBlank()) {
                ProtectionManager.INSTANCE.register(entry);
                AtlantisMod.LOGGER.info("Loaded active protection {}.", entry.id());
            }
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to load active protection file {}: {}", file, e.getMessage());
        }
    }
}
