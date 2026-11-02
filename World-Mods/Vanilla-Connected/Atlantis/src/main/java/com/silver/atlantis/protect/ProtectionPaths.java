package com.silver.atlantis.protect;

import com.silver.atlantis.construct.undo.UndoPaths;

import java.nio.file.Path;

/**
 * File locations for persisted protection data.
 */
public final class ProtectionPaths {

    private ProtectionPaths() {
    }

    public static Path activeProtectionFile() {
        return UndoPaths.undoBaseDir().resolve("active.atlprotect");
    }
}
