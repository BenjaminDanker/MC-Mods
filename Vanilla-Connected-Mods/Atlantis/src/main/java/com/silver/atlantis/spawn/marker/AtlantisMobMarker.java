package com.silver.atlantis.spawn.marker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.ErrorReporter;

/**
 * Persistent marker for a mob that can be spawned/despawned by proximity.
 */
public record AtlantisMobMarker(String entityTypeId, String entityNbtSnbt, boolean wasAlive, float yaw, float pitch) {

    public static final Codec<AtlantisMobMarker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("entityTypeId").forGetter(AtlantisMobMarker::entityTypeId),
        Codec.STRING.fieldOf("entityNbtSnbt").forGetter(AtlantisMobMarker::entityNbtSnbt),
        Codec.BOOL.optionalFieldOf("wasAlive", true).forGetter(AtlantisMobMarker::wasAlive),
        Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(AtlantisMobMarker::yaw),
        Codec.FLOAT.optionalFieldOf("pitch", 0.0f).forGetter(AtlantisMobMarker::pitch)
    ).apply(instance, AtlantisMobMarker::new));

    public static AtlantisMobMarker fromEntity(MobEntity entity) {
        if (entity == null) {
            return null;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (typeId == null) {
            return null;
        }

        NbtCompound nbt = new NbtCompound();
        NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, entity.getRegistryManager());
        if (!entity.saveData(writeView)) {
            return null;
        }
        nbt.copyFrom(writeView.getNbt());

        String snbt;
        try {
            snbt = NbtHelper.toNbtProviderString(nbt);
        } catch (Exception e) {
            return null;
        }

        return new AtlantisMobMarker(typeId.toString(), snbt, true, entity.getYaw(), entity.getPitch());
    }

    public NbtCompound toEntityNbt() {
        try {
            NbtCompound parsed = NbtHelper.fromNbtProviderString(entityNbtSnbt);
            return parsed == null ? new NbtCompound() : parsed;
        } catch (Exception ignored) {
            return new NbtCompound();
        }
    }

    public MobEntity createMob(ServerWorld world) {
        if (world == null) {
            return null;
        }

        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) {
            return null;
        }

        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        if (type == null || (type == EntityType.PIG && !"pig".equals(id.getPath()))) {
            return null;
        }

        Entity entity = type.create(world, net.minecraft.entity.SpawnReason.COMMAND);
        if (!(entity instanceof MobEntity mob)) {
            return null;
        }

        NbtCompound nbt = toEntityNbt();
        // Remove UUID to prevent collisions when spawning from markers
        nbt.remove("UUID");
        try {
            net.minecraft.storage.ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt);
            mob.readData(readView);
        } catch (Exception ignored) {
            return null;
        }

        // Mark mob to prevent despawning
        mob.addCommandTag("no_despawn");

        return mob;
    }
}