package com.silver.atlantis.construct.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import java.util.function.Supplier;

@Mixin(VersionedChunkStorage.class)
public interface VersionedChunkStorageAccessor {

    @Invoker("getStorageKey")
    StorageKey atlantis$invokeGetStorageKey();

    @Invoker("updateChunkNbt")
    NbtCompound atlantis$invokeUpdateChunkNbt(
        RegistryKey<World> worldKey,
        Supplier<PersistentStateManager> persistentStateManager,
        NbtCompound nbt,
        Optional<RegistryKey<MapCodec<? extends ChunkGenerator>>> generatorCodecKey
    );
}
