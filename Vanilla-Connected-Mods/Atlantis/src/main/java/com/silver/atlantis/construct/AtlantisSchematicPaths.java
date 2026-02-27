package com.silver.atlantis.construct;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class AtlantisSchematicPaths {

    private static final String SCHEMATIC_FILE = "atlantis.schem";

    private AtlantisSchematicPaths() {
    }

    public static Path schematicDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("atlantis");
    }

    public static Path schematicFile() {
        return schematicDir().resolve(SCHEMATIC_FILE);
    }
}