package com.silver.magitech;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyrptonaught.customportalapi.portal.PortalIgnitionSource;
import net.kyrptonaught.customportalapi.portal.PortalPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each faction arrives on its own landing dock: a flat stone pad on the nearest land to its corner of the map, with a lit
 * gate back to its home server. Built once; where it ended up is written to docks.json and logged, because the arrival
 * point has to be copied into the portal config of the home server.
 */
final class Landings {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final int PAD_RADIUS = 6;

    private final MinecraftServer server;
    private final MagitechConfig config;
    private final Path file;

    Landings(MinecraftServer server, MagitechConfig config) {
        this.server = server;
        this.config = config;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve("magitech").resolve("docks.json");
    }

    void ensure() {
        JsonObject state = read();
        ServerLevel level = server.overworld();
        for (MagitechConfig.Dock dock : new MagitechConfig.Dock[] {config.magicDock, config.techDock}) {
            if (state.has(dock.faction)) {
                JsonObject at = state.getAsJsonObject(dock.faction);
                LOGGER.info("Dock {} stands; arrival {} {} {}", dock.faction, at.get("x").getAsDouble(), at.get("y").getAsDouble(), at.get("z").getAsDouble());
                continue;
            }
            try {
                state.add(dock.faction, build(level, dock));
                write(state);
            } catch (RuntimeException failure) {
                LOGGER.error("ALERT: could not build the {} dock", dock.faction, failure);
            }
        }
    }

    private JsonObject build(ServerLevel level, MagitechConfig.Dock dock) {
        BlockPos spot = findLand(level, dock.x, dock.z);
        int cx = spot.getX();
        int cz = spot.getZ();
        int y = spot.getY();
        Block frame = BuiltInRegistries.BLOCK.getValue(Identifier.parse(dock.frameBlock));
        // Pad, with the air above it cleared so nobody arrives inside a hill.
        for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
            for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 7; dy++) {
                    level.setBlock(new BlockPos(cx + dx, y + dy, cz + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int[] corner : new int[][] {{-PAD_RADIUS, -PAD_RADIUS}, {PAD_RADIUS, -PAD_RADIUS}, {-PAD_RADIUS, PAD_RADIUS}, {PAD_RADIUS, PAD_RADIUS}}) {
            level.setBlock(new BlockPos(cx + corner[0], y, cz + corner[1]), Blocks.LANTERN.defaultBlockState(), 2);
        }
        // The gate: a frame four wide and five tall on the pad's north side, interior two by three.
        int gateZ = cz - 3;
        for (int gx = -2; gx <= 1; gx++) {
            for (int gy = -1; gy <= 3; gy++) {
                boolean border = gx == -2 || gx == 1 || gy == -1 || gy == 3;
                level.setBlock(new BlockPos(cx + gx, y + gy, gateZ), border ? frame.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        Item ignition = BuiltInRegistries.ITEM.getValue(Identifier.parse(config.ignitionItem));
        boolean lit = PortalPlacer.attemptPortalLight(level, new BlockPos(cx - 1, y, gateZ), PortalIgnitionSource.ItemUseSource(ignition));
        JsonObject at = new JsonObject();
        at.addProperty("x", cx - 0.5);
        at.addProperty("y", (double) y);
        at.addProperty("z", cz + 2.5);
        LOGGER.info("Built the {} dock at {} {} {}; gate lit={}; ARRIVAL x={} y={} z={} (copy into the home server's portal config)",
            dock.faction, cx, y, cz, lit, cx - 0.5, y, cz + 2.5);
        return at;
    }

    /** The nearest flat dry land to (x, z), searched in widening squares. */
    private BlockPos findLand(ServerLevel level, int x, int z) {
        for (int ring = 0; ring <= 40; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = x + dx * 24;
                    int cz = z + dz * 24;
                    level.getChunk(cx >> 4, cz >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    if (y < level.getSeaLevel() + 1 || level.getBiome(new BlockPos(cx, y, cz)).is(BiomeTags.IS_OCEAN)
                            || level.getBiome(new BlockPos(cx, y, cz)).is(BiomeTags.IS_RIVER)) {
                        continue;
                    }
                    if (flat(level, cx, cz, y)) {
                        return new BlockPos(cx, y, cz);
                    }
                }
            }
        }
        throw new IllegalStateException("no flat land within 960 blocks of " + x + "," + z);
    }

    private boolean flat(ServerLevel level, int cx, int cz, int y) {
        for (int[] offset : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {0, 6}, {0, -6}}) {
            level.getChunk((cx + offset[0]) >> 4, (cz + offset[1]) >> 4);
            int other = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + offset[0], cz + offset[1]);
            if (Math.abs(other - y) > 3) {
                return false;
            }
        }
        return true;
    }

    private JsonObject read() {
        try {
            if (Files.exists(file)) {
                return new Gson().fromJson(Files.readString(file), JsonObject.class);
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}", file, failure);
        }
        return new JsonObject();
    }

    private void write(JsonObject state) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new Gson().toJson(state));
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not save {}", file, failure);
        }
    }
}
