package com.silver.magitech;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each faction arrives on its own landing dock: a flat stone pad with a lit gate back to its home server. Both docks
 * stand on one continent, each on its own side of it (magic to the west, tech to the east by default), so the two
 * factions can always walk to each other. Built once; docks.json records where. When a dock is built or moved, its
 * arrival point is written into this server's portals.json, which takes effect at the next start.
 */
final class Landings {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final int PAD_RADIUS = 6;

    private final MinecraftServer server;
    private final MagitechConfig config;
    private final Path file;
    /** docks.json as last read or written; set on the server thread. */
    private volatile JsonObject state;

    Landings(MinecraftServer server, MagitechConfig config) {
        this.server = server;
        this.config = config;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve("magitech").resolve("docks.json");
    }

    /** Server thread. The continent is read off the server thread (noise only); building happens back on it. */
    void ensure() {
        JsonObject state = read();
        ServerLevel level = server.overworld();
        if (state.has(config.magicDock.faction) && state.has(config.techDock.faction) && state.has("continent")
                && !moved(state, config.magicDock) && !moved(state, config.techDock)) {
            for (MagitechConfig.Dock dock : docks()) {
                Vec3 at = arrival(state, dock.faction);
                LOGGER.info("Dock {} stands; arrival {} {} {}", dock.faction, at.x, at.y, at.z);
            }
            this.state = state;
            return;
        }
        CompletableFuture.supplyAsync(() -> continentFor(level, state)).whenComplete((continent, failure) -> server.execute(() -> {
            if (failure != null) {
                LOGGER.error("ALERT: could not map the continent; docks left as they are", failure);
                this.state = state;
                return;
            }
            place(level, state, continent);
        }));
    }

    private MagitechConfig.Dock[] docks() {
        return new MagitechConfig.Dock[] {config.magicDock, config.techDock};
    }

    /**
     * The landmass both docks must share. With docks already built: the one under the magic dock, or under the tech
     * dock if that is larger. Otherwise the land nearest the middle of the two dock targets.
     */
    private ContinentMap continentFor(ServerLevel level, JsonObject state) {
        ContinentMap best = null;
        for (MagitechConfig.Dock dock : docks()) {
            if (state.has(dock.faction)) {
                Vec3 at = arrival(state, dock.faction);
                ContinentMap map = ContinentMap.around(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
                if (best == null || map.size() > best.size()) {
                    best = map;
                }
            }
        }
        if (best != null && best.size() > 0) {
            return best;
        }
        int midX = (config.magicDock.x + config.techDock.x) / 2;
        int midZ = (config.magicDock.z + config.techDock.z) / 2;
        for (int ring = 0; ring <= ContinentMap.RADIUS / ContinentMap.STEP; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    ContinentMap map = ContinentMap.around(level, midX + dx * ContinentMap.STEP, midZ + dz * ContinentMap.STEP);
                    if (map.size() > 0) {
                        return map;
                    }
                }
            }
        }
        throw new IllegalStateException("no land within " + ContinentMap.RADIUS + " blocks of " + midX + "," + midZ);
    }

    private void place(ServerLevel level, JsonObject state, ContinentMap continent) {
        boolean moved = false;
        for (MagitechConfig.Dock dock : docks()) {
            if (state.has(dock.faction)) {
                Vec3 at = arrival(state, dock.faction);
                if (moved(state, dock)) {
                    LOGGER.warn("Dock {} has a new target ({} {}); moving it", dock.faction, dock.x, dock.z);
                } else if (continent.touches(at.x, at.z)) {
                    LOGGER.info("Dock {} stands on the shared continent; arrival {} {} {}", dock.faction, at.x, at.y, at.z);
                    continue;
                } else {
                    LOGGER.warn("Dock {} at {} {} is on another landmass; moving it to the shared continent", dock.faction, at.x, at.z);
                }
                removeGate(level, at, dock);
            }
            int[] target = continent.nearest(dock.x, dock.z);
            try {
                JsonObject at = build(level, dock, target[0], target[1], continent);
                JsonObject wanted = new JsonObject();
                wanted.addProperty("x", dock.x);
                wanted.addProperty("z", dock.z);
                at.add("target", wanted);
                state.add(dock.faction, at);
                writePortalArrival(dock, at);
                moved = true;
            } catch (RuntimeException failure) {
                LOGGER.error("ALERT: could not build the {} dock", dock.faction, failure);
            }
        }
        state.addProperty("continent", continent.size());
        write(state);
        this.state = state;
        if (moved) {
            LOGGER.warn("ALERT: a dock was built or moved; restart this server so its portal arrivals take effect");
        }
    }

    /**
     * True when the dock was built for another target than the config's now. Docks from before targets were recorded
     * were built for the old default, 2000 blocks either side of 0.
     */
    private static boolean moved(JsonObject state, MagitechConfig.Dock dock) {
        if (!state.has(dock.faction)) {
            return false;
        }
        JsonObject at = state.getAsJsonObject(dock.faction);
        int x = at.has("target") ? at.getAsJsonObject("target").get("x").getAsInt() : ("magic".equals(dock.faction) ? -2000 : 2000);
        int z = at.has("target") ? at.getAsJsonObject("target").get("z").getAsInt() : 0;
        return x != dock.x || z != dock.z;
    }

    /** Where the given faction arrives and respawns (no bed), once the docks are known. */
    Optional<Vec3> arrival(String faction) {
        JsonObject known = state;
        return known != null && known.has(faction) ? Optional.of(arrival(known, faction)) : Optional.empty();
    }

    private static Vec3 arrival(JsonObject state, String faction) {
        JsonObject at = state.getAsJsonObject(faction);
        return new Vec3(at.get("x").getAsDouble(), at.get("y").getAsDouble(), at.get("z").getAsDouble());
    }

    /** Clears the old gate (frame and portal) of a dock that moves; the pad is left as ruins. */
    private void removeGate(ServerLevel level, Vec3 at, MagitechConfig.Dock dock) {
        int cx = (int) Math.round(at.x + 0.5);
        int cz = (int) Math.round(at.z - 2.5);
        int y = (int) at.y;
        int gateZ = cz - 3;
        for (int gx = -2; gx <= 1; gx++) {
            for (int gy = -1; gy <= 3; gy++) {
                level.setBlock(new BlockPos(cx + gx, y + gy, gateZ), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        LOGGER.info("Removed the old {} gate at {} {} {}", dock.faction, cx, y, gateZ);
    }

    /** Points this dock's portal arrival at the new spot (Portals reads it at the next start). */
    private void writePortalArrival(MagitechConfig.Dock dock, JsonObject at) {
        if (com.silver.portals.Portals.setArrival(server, dock.portal, "minecraft:overworld",
                at.get("x").getAsDouble(), at.get("y").getAsDouble(), at.get("z").getAsDouble())) {
            LOGGER.info("Portal {} now arrives at {} {} {}", dock.portal, at.get("x"), at.get("y"), at.get("z"));
        } else {
            LOGGER.error("ALERT: portals.json has no portal {}; set its arrival by hand", dock.portal);
        }
    }

    private JsonObject build(ServerLevel level, MagitechConfig.Dock dock, int targetX, int targetZ, ContinentMap continent) {
        BlockPos spot = findLand(level, targetX, targetZ, continent);
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
        boolean lit = com.silver.portals.Portals.light(level, new BlockPos(cx - 1, y, gateZ), ignition, null);
        JsonObject at = new JsonObject();
        at.addProperty("x", cx - 0.5);
        at.addProperty("y", (double) y);
        at.addProperty("z", cz + 2.5);
        LOGGER.info("Built the {} dock at {} {} {}; gate lit={}; arrival x={} y={} z={}", dock.faction, cx, y, cz, lit, cx - 0.5, y, cz + 2.5);
        return at;
    }

    /** The nearest flat dry land to (x, z) on the continent, searched in widening squares. */
    private BlockPos findLand(ServerLevel level, int x, int z, ContinentMap continent) {
        for (int ring = 0; ring <= 40; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = x + dx * 24;
                    int cz = z + dz * 24;
                    if (!continent.contains(cx, cz)) {
                        continue;
                    }
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
            writeAtomically(file, new Gson().toJson(state));
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not save {}", file, failure);
        }
    }

    /** Writes through a temporary sibling, so a crash never leaves a half-written file. */
    private static void writeAtomically(Path target, String text) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, text);
        Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
