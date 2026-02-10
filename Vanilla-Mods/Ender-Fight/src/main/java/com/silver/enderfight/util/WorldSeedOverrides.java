package com.silver.enderfight.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for custom world seeds that can't be set directly.
 */
public class WorldSeedOverrides {
    private static final Map<RegistryKey<World>, Long> SEED_OVERRIDES = new ConcurrentHashMap<>();
    
    public static void setSeedOverride(RegistryKey<World> worldKey, long seed) {
        SEED_OVERRIDES.put(worldKey, seed);
    }
    
    public static Long getSeedOverride(RegistryKey<World> worldKey) {
        return SEED_OVERRIDES.get(worldKey);
    }
    
    public static void removeSeedOverride(RegistryKey<World> worldKey) {
        SEED_OVERRIDES.remove(worldKey);
    }
    
    public static void clear() {
        SEED_OVERRIDES.clear();
    }
}
