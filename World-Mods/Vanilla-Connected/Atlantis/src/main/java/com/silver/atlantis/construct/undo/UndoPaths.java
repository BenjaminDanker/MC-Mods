package com.silver.atlantis.construct.undo;

import com.silver.atlantis.construct.AtlantisSchematicPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class UndoPaths {

    private UndoPaths() {
    }

    public static Path undoBaseDir() {
        return AtlantisSchematicPaths.schematicDir().resolve("undo");
    }

    public static Path runDir(String runId) {
        return undoBaseDir().resolve(runId);
    }

    public static Path metadataFile(Path runDir) {
        return runDir.resolve("metadata.json");
    }

    public static Path constructUndoFile(Path runDir) {
        return runDir.resolve("construct.atlundo");
    }

    /**
     * Finds the newest undo run directory by numeric name that has both metadata and undo file.
     * Returns null if no usable run exists.
     */
    public static String findLatestUsableRunIdOrNull() {
        try {
            return findLatestUsableRunId();
        } catch (java.io.IOException e) {
            com.silver.atlantis.AtlantisMod.LOGGER.error("[undo] Could not list undo runs under {}", undoBaseDir(), e);
            return null;
        }
    }

    /** Like {@link #findLatestUsableRunIdOrNull} but an unreadable undo folder is an error, not "no history". */
    public static String findLatestUsableRunId() throws java.io.IOException {
        Path base = undoBaseDir();
        if (!Files.exists(base)) {
            return null;
        }
        try (java.util.stream.Stream<Path> children = Files.list(base)) {
            return children
                .filter(Files::isDirectory)
                .filter(UndoPaths::hasNumericDirectoryName)
                .sorted(Comparator.comparingLong(UndoPaths::numericDirectoryName).reversed())
                .filter(UndoPaths::isUsableRunDirectory)
                .map(path -> path.getFileName().toString())
                .findFirst()
                .orElse(null);
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Deletes numeric undo run directories that are not usable (missing metadata.json or construct.atlundo).
     * Returns number of pruned directories.
     */
    public static int pruneUnusableRunDirectories() {
        try {
            Path base = undoBaseDir();
            if (!Files.exists(base)) {
                return 0;
            }

            int deleted = 0;
            try (java.util.stream.Stream<Path> children = Files.list(base)) {
                for (Path child : children.filter(Files::isDirectory).filter(UndoPaths::hasNumericDirectoryName).toList()) {
                    if (isUsableRunDirectory(child)) {
                        continue;
                    }
                    if (deleteRecursively(child)) {
                        deleted++;
                    }
                }
            }

            return deleted;
        } catch (Exception e) {
            com.silver.atlantis.AtlantisMod.LOGGER.error("[undo] Could not prune unusable undo runs under {}", undoBaseDir(), e);
            return 0;
        }
    }

    private static boolean isUsableRunDirectory(Path runDir) {
        return Files.exists(metadataFile(runDir)) && Files.exists(constructUndoFile(runDir));
    }

    private static boolean hasNumericDirectoryName(Path runDir) {
        String name = runDir.getFileName().toString();
        try {
            Long.parseLong(name);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static long numericDirectoryName(Path runDir) {
        return Long.parseLong(runDir.getFileName().toString());
    }

    private static boolean deleteRecursively(Path root) {
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
