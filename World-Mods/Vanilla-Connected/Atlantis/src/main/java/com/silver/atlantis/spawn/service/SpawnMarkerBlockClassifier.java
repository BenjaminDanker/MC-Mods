package com.silver.atlantis.spawn.service;

import com.silver.atlantis.spawn.config.SpawnMobConfig;

import java.util.Locale;

final class SpawnMarkerBlockClassifier {

    private SpawnMarkerBlockClassifier() {
    }

    static SpawnMobConfig.SpawnType spawnTypeFor(String blockId) {
        String base = baseBlockName(blockId);
        if ("minecraft:sandstone".equals(base)) {
            return SpawnMobConfig.SpawnType.LAND;
        }
        if ("minecraft:diamond_block".equals(base)) {
            return SpawnMobConfig.SpawnType.WATER;
        }
        if ("minecraft:white_stained_glass".equals(base)) {
            return SpawnMobConfig.SpawnType.BOSS;
        }
        return null;
    }

    static boolean isWaterLike(String blockId) {
        return "minecraft:water".equals(baseBlockName(blockId));
    }

    static boolean isAirOrWaterLike(String blockId) {
        String base = baseBlockName(blockId);
        return "minecraft:air".equals(base)
            || "minecraft:cave_air".equals(base)
            || "minecraft:void_air".equals(base)
            || "minecraft:water".equals(base);
    }

    static boolean isAirLike(String blockId) {
        String base = baseBlockName(blockId);
        return "minecraft:air".equals(base)
            || "minecraft:cave_air".equals(base)
            || "minecraft:void_air".equals(base);
    }

    static String baseBlockName(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "minecraft:air";
        }

        String normalized = blockId.trim().toLowerCase(Locale.ROOT);
        int propsStart = normalized.indexOf('[');
        int nbtStart = normalized.indexOf('{');

        int cut = normalized.length();
        if (propsStart >= 0 && propsStart < cut) {
            cut = propsStart;
        }
        if (nbtStart >= 0 && nbtStart < cut) {
            cut = nbtStart;
        }

        String base = normalized.substring(0, cut).trim();
        return base.isEmpty() ? "minecraft:air" : base;
    }
}
