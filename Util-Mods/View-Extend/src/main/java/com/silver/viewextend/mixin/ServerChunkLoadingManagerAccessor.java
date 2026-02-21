package com.silver.viewextend.mixin;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.storage.VersionedChunkStorage;
import net.minecraft.world.storage.StorageKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VersionedChunkStorage.class)
public interface ServerChunkLoadingManagerAccessor {
    @Invoker("getStorageKey")
    StorageKey viewextend$invokeGetStorageKey();

    @Invoker("updateChunkNbt")
    NbtCompound viewextend$invokeUpdateChunkNbt(
            RegistryKey<World> worldKey,
            Supplier<PersistentStateManager> persistentStateManager,
            NbtCompound nbt,
            Optional<RegistryKey<MapCodec<? extends ChunkGenerator>>> generatorCodecKey);
}