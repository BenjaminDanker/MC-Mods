package com.silver.enderfight.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Accesses internal MinecraftServer state required for rebuilding worlds at runtime.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getSession();

    @Accessor("executor")
    Executor getWorkerExecutor();

    @Accessor("worldData")
    WorldData getSaveProperties();

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getWorlds();
}
