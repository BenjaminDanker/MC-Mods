package com.silver.enderfight.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Thread-safe storage for custom world seeds that can't be set directly.
 */
public class WorldSeedOverrides {
    private static final Map<ResourceKey<Level>, Long> SEED_OVERRIDES = new ConcurrentHashMap<>();
    
    public static void setSeedOverride(ResourceKey<Level> worldKey, long seed) {
        SEED_OVERRIDES.put(worldKey, seed);
    }
    
    public static Long getSeedOverride(ResourceKey<Level> worldKey) {
        return SEED_OVERRIDES.get(worldKey);
    }
    
    public static void removeSeedOverride(ResourceKey<Level> worldKey) {
        SEED_OVERRIDES.remove(worldKey);
    }
    
    public static void clear() {
        SEED_OVERRIDES.clear();
    }
}
