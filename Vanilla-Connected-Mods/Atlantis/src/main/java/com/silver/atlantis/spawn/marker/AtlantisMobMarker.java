package com.silver.atlantis.spawn.marker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

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

    public static AtlantisMobMarker fromEntity(Mob entity) {
        if (entity == null) {
            return null;
        }

        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) {
            return null;
        }

        CompoundTag nbt = new CompoundTag();
        TagValueOutput writeView = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        if (!entity.save(writeView)) {
            return null;
        }
        nbt.merge(writeView.buildResult());

        String snbt;
        try {
            snbt = NbtUtils.structureToSnbt(nbt);
        } catch (Exception e) {
            return null;
        }

        return new AtlantisMobMarker(typeId.toString(), snbt, true, entity.getYRot(), entity.getXRot());
    }

    public CompoundTag toEntityNbt() {
        try {
            CompoundTag parsed = NbtUtils.snbtToStructure(entityNbtSnbt);
            return parsed == null ? new CompoundTag() : parsed;
        } catch (Exception ignored) {
            return new CompoundTag();
        }
    }

    public Mob createMob(ServerLevel world) {
        if (world == null) {
            return null;
        }

        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) {
            return null;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) {
            return null;
        }

        Entity entity = type.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (!(entity instanceof Mob mob)) {
            return null;
        }

        CompoundTag nbt = toEntityNbt();
        // Remove UUID to prevent collisions when spawning from markers
        nbt.remove("UUID");
        try {
            net.minecraft.world.level.storage.ValueInput readView = TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), nbt);
            mob.load(readView);
        } catch (Exception ignored) {
            return null;
        }

        // Mark mob to prevent despawning
        mob.addTag("no_despawn");

        return mob;
    }
}
