package com.silver.arcade.realm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which climb tiers each room appears at, and how hard it plays there. The climb tier is the reward ladder; each room
 * has its own difficulty level underneath, set in config/arcade-difficulty.json (re-read when the file changes):
 * <pre>
 * { "connect4": { "levels": { "1": 1, "2": 1, "3": 2, "5": 3 }, "after": "stop" },
 *   "maze":     { "levels": { "2": 1, "3": 2 }, "after": "grow" } }
 * </pre>
 * A climb tier listed maps to that room level. Tiers below the first listed one: the room is not offered. Tiers above
 * the last: "stop" (default) = not offered, "hold" = the last level, "grow" = the last level plus one per tier.
 * Tiers between listed ones use the level of the nearest listed tier below. A level of 0 means "not at this tier".
 * Rooms without an entry use their climb tier as their level (from tier 1, growing).
 */
public final class Difficulty {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");

    static final class Curve {
        Map<String, Integer> levels = new HashMap<>();
        String after = "stop";
    }

    private static Map<String, TreeMap<Integer, Integer>> levels = Map.of();
    private static Map<String, String> after = Map.of();
    private static long loadedMtime = -1;

    private Difficulty() {
    }

    /** True when the room has a curve in the file (then its first tier comes from the curve, not the catalogue). */
    public static synchronized boolean hasCurve(String room) {
        reload();
        return levels.containsKey(room);
    }

    /** The room's own level at this climb tier, or 0 when the room is not offered there. */
    public static synchronized int level(String room, int tier) {
        reload();
        TreeMap<Integer, Integer> map = levels.get(room);
        if (map == null || map.isEmpty()) {
            return tier;
        }
        var at = map.floorEntry(tier);
        if (at == null) {
            return 0;
        }
        if (tier > map.lastKey()) {
            return switch (after.getOrDefault(room, "stop")) {
                case "hold" -> map.lastEntry().getValue();
                case "grow" -> map.lastEntry().getValue() + (tier - map.lastKey());
                default -> 0;
            };
        }
        return at.getValue();
    }

    private static void reload() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("arcade-difficulty.json");
        try {
            long mtime = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
            if (mtime == loadedMtime) {
                return;
            }
            loadedMtime = mtime;
            if (mtime == 0) {
                levels = Map.of();
                after = Map.of();
                return;
            }
            Map<String, Curve> curves = new Gson().fromJson(Files.readString(file), new TypeToken<Map<String, Curve>>() { }.getType());
            Map<String, TreeMap<Integer, Integer>> nextLevels = new HashMap<>();
            Map<String, String> nextAfter = new HashMap<>();
            curves.forEach((room, curve) -> {
                TreeMap<Integer, Integer> map = new TreeMap<>();
                curve.levels.forEach((t, l) -> map.put(Integer.parseInt(t.trim()), l));
                nextLevels.put(room, map);
                nextAfter.put(room, curve.after == null ? "stop" : curve.after);
            });
            levels = nextLevels;
            after = nextAfter;
            LOGGER.info("Arcade difficulty curves loaded for {} rooms", levels.size());
        } catch (Exception failure) {
            LOGGER.error("ALERT: config/arcade-difficulty.json could not be read; keeping the previous curves", failure);
        }
    }
}
