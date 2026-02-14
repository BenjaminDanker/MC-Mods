package com.silver.spawnprotect.protect;

import com.silver.spawnprotect.SpawnProtectMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Simple properties-backed config for spawn protection bounds.
 */
public final class SpawnProtectConfig {

    private final boolean enabled;
    private final String dimensionId;
    private final Identifier dimensionKey;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final boolean disablePvp;
    private final boolean allowOpBypass;
    private final Box protectedBox;

    private SpawnProtectConfig(
        boolean enabled,
        String dimensionId,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean disablePvp,
        boolean allowOpBypass
    ) {
        Identifier parsed = Identifier.tryParse((dimensionId != null) ? dimensionId.trim() : "");
        if (parsed == null) {
            parsed = Identifier.of("minecraft", "overworld");
        }

        this.enabled = enabled;
        this.dimensionKey = parsed;
        this.dimensionId = parsed.toString();
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.disablePvp = disablePvp;
        this.allowOpBypass = allowOpBypass;
        this.protectedBox = new Box(
            this.minX,
            this.minY,
            this.minZ,
            this.maxX + 1.0D,
            this.maxY + 1.0D,
            this.maxZ + 1.0D
        );
    }

    public static SpawnProtectConfig defaultConfig() {
        return new SpawnProtectConfig(true, "minecraft:overworld", -64, -64, -64, 64, 320, 64, true, true);
    }

    public static SpawnProtectConfig loadOrCreate(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());

            if (!Files.exists(configFile)) {
                writeDefaults(configFile, defaultConfig());
                SpawnProtectMod.LOGGER.info("Created default Spawn Protect config at {}", configFile);
                return defaultConfig();
            }

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            }

            boolean enabled = getBoolean(props, "enabled", true);
            String dimensionId = props.getProperty("dimension", "minecraft:overworld").trim();

            int minX = getInt(props, "minX", -64);
            int minY = getInt(props, "minY", -64);
            int minZ = getInt(props, "minZ", -64);
            int maxX = getInt(props, "maxX", 64);
            int maxY = getInt(props, "maxY", 320);
            int maxZ = getInt(props, "maxZ", 64);

            boolean disablePvp = getBoolean(props, "disablePvp", true);
            boolean allowOpBypass = getBoolean(props, "allowOpBypass", true);

            return new SpawnProtectConfig(enabled, dimensionId, minX, minY, minZ, maxX, maxY, maxZ, disablePvp, allowOpBypass);
        } catch (Exception e) {
            SpawnProtectMod.LOGGER.warn("Failed to load Spawn Protect config: {}", e.getMessage());
            return defaultConfig();
        }
    }

    private static void writeDefaults(Path configFile, SpawnProtectConfig defaults) throws IOException {
        String content = """
            # Spawn Protect configuration
            # Bounds are inclusive and define a full protected box.
            # Dimension id example: minecraft:overworld
            enabled=%s
            dimension=%s
            minX=%d
            minY=%d
            minZ=%d
            maxX=%d
            maxY=%d
            maxZ=%d
            disablePvp=%s
            allowOpBypass=%s
            """.formatted(
            defaults.enabled,
            defaults.dimensionId,
            defaults.minX,
            defaults.minY,
            defaults.minZ,
            defaults.maxX,
            defaults.maxY,
            defaults.maxZ,
            defaults.disablePvp,
            defaults.allowOpBypass
        );

        try (OutputStream out = Files.newOutputStream(configFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static int getInt(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public boolean enabled() {
        return enabled;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public Identifier dimensionKey() {
        return dimensionKey;
    }

    public boolean contains(String checkDimension, int x, int y, int z) {
        if (!enabled || checkDimension == null || !dimensionId.equals(checkDimension)) {
            return false;
        }

        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean contains(ServerWorld world, BlockPos pos) {
        if (!enabled || world == null || pos == null) {
            return false;
        }

        if (!dimensionKey.equals(world.getRegistryKey().getValue())) {
            return false;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean disablePvp() {
        return disablePvp;
    }

    public boolean allowOpBypass() {
        return allowOpBypass;
    }

    public boolean matchesDimension(String checkDimension) {
        return enabled && checkDimension != null && dimensionId.equals(checkDimension);
    }

    public boolean matchesDimension(Identifier checkDimension) {
        return enabled && checkDimension != null && dimensionKey.equals(checkDimension);
    }

    public Box protectedBox() {
        return protectedBox;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }
}
