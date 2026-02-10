package com.silver.atlantis.cycle;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class CyclePaths {

    private CyclePaths() {
    }

    public static Path baseDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("atlantis");
    }

    public static Path stateFile() {
        return baseDir().resolve("cycle_state.json");
    }
}
