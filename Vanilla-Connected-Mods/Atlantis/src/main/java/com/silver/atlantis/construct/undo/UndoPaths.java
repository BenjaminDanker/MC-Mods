package com.silver.atlantis.construct.undo;

import com.silver.atlantis.construct.SchematicSliceScanner;

import java.nio.file.Path;

public final class UndoPaths {

    private UndoPaths() {
    }

    public static Path undoBaseDir() {
        return SchematicSliceScanner.defaultSchematicDir().resolve("undo");
    }

    public static Path runDir(String runId) {
        return undoBaseDir().resolve(runId);
    }

    public static Path metadataFile(Path runDir) {
        return runDir.resolve("metadata.json");
    }

    public static Path sliceUndoFile(Path runDir, int sliceIndex) {
        return runDir.resolve(String.format("slice_%03d.atlundo", sliceIndex));
    }

    /**
     * Finds the newest undo run directory by numeric name (millis-based runId).
     * Returns null if none exist.
     */
    public static String findLatestRunIdOrNull() {
        try {
            Path base = undoBaseDir();
            if (!java.nio.file.Files.exists(base)) {
                return null;
            }

            String newest = null;
            long newestId = Long.MIN_VALUE;

            try (java.util.stream.Stream<Path> children = java.nio.file.Files.list(base)) {
                for (Path child : children.filter(java.nio.file.Files::isDirectory).toList()) {
                    String name = child.getFileName().toString();
                    try {
                        long id = Long.parseLong(name);
                        if (id > newestId) {
                            newestId = id;
                            newest = name;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            return newest;
        } catch (Exception ignored) {
            return null;
        }
    }
}
