package com.silver.atlantis.protect;

import com.silver.atlantis.construct.undo.UndoPaths;

import java.nio.file.Path;

/**
 * File locations for persisted protection data.
 */
public final class ProtectionPaths {

    private ProtectionPaths() {
    }

    public static Path protectionFile(Path runDir) {
        return runDir.resolve("protection.atlprotect");
    }

    public static Path protectionFileForRun(String runId) {
        return protectionFile(UndoPaths.runDir(runId));
    }
}
