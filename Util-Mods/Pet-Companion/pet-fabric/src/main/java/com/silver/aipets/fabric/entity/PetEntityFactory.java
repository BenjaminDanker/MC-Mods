package com.silver.aipets.fabric.entity;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.ResourceId;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.mixin.CatEntityVariantInvoker;
import com.silver.aipets.fabric.mixin.WolfEntityVariantInvoker;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;

/** Creates an in-memory vanilla representation without spawning or writing authority. */
public final class PetEntityFactory {
    public PreparedPetEntity prepare(
            ServerLevel world,
            Pet pet,
            UUID entityUuid,
            WorldPosition position,
            boolean sleeping) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(position, "position");

        TamableAnimal entity = createVanillaEntity(world, pet.appearance().species());
        entity.setUUID(entityUuid);
        entity.snapTo(position.x(), position.y(), position.z(), 0.0F, 0.0F);
        entity.setAge(0);
        entity.setCustomName(Component.literal(pet.name()));
        entity.setCustomNameVisible(true);

        VariantApplication variant = applyVariant(world, entity, pet);
        AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
        if (scale == null) {
            throw new IllegalStateException("Target entity has no generic scale attribute");
        }
        scale.setBaseValue(pet.appearance().scale());

        ((PetEntityData) entity).aipets$mark(
                pet.petId(), pet.ownerUuid(), pet.recordVersion(), sleeping);
        PetEntityController.configure(entity);

        if (variant.repaired()) {
            PetCompanionMod.LOGGER.warn(StructuredPetEvent
                    .operation("appearance_compatibility_repair")
                    .pet(pet.petId()).owner(pet.ownerUuid())
                    .outcome("deterministic_fallback")
                    .toJson());
        }
        return new PreparedPetEntity(entity, variant.effectiveId(), variant.repaired());
    }

    private static TamableAnimal createVanillaEntity(ServerLevel world, PetSpecies species) {
        TamableAnimal entity = switch (species) {
            case CAT -> EntityTypes.CAT.create(world, EntitySpawnReason.COMMAND);
            case DOG -> EntityTypes.WOLF.create(world, EntitySpawnReason.COMMAND);
        };
        if (entity == null) {
            throw new IllegalStateException("Minecraft failed to construct " + species + " entity");
        }
        return entity;
    }

    private static VariantApplication applyVariant(
            ServerLevel world, TamableAnimal entity, Pet pet) {
        Identifier requested = Identifier.parse(pet.appearance().variantId().value());
        long seed = pet.appearance().appearanceSeed().orElse(
                pet.petId().getMostSignificantBits() ^ pet.petId().getLeastSignificantBits());

        if (entity instanceof Cat cat) {
            Registry<CatVariant> registry = world.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
            Holder.Reference<CatVariant> entry = registry.get(requested).orElse(null);
            boolean repaired = entry == null;
            ResourceId effective = repaired ? fallback(registry, seed) : ResourceId.parse(requested.toString());
            if (entry == null) {
                entry = registry.get(Identifier.parse(effective.value())).orElseThrow();
            }
            ((CatEntityVariantInvoker) cat).aipets$setVariant(entry);
            return new VariantApplication(effective, repaired);
        }
        if (entity instanceof Wolf wolf) {
            Registry<WolfVariant> registry = world.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);
            Holder.Reference<WolfVariant> entry = registry.get(requested).orElse(null);
            boolean repaired = entry == null;
            ResourceId effective = repaired ? fallback(registry, seed) : ResourceId.parse(requested.toString());
            if (entry == null) {
                entry = registry.get(Identifier.parse(effective.value())).orElseThrow();
            }
            ((WolfEntityVariantInvoker) wolf).aipets$setVariant(entry);
            return new VariantApplication(effective, repaired);
        }
        throw new IllegalArgumentException("Only cat/wolf entities can represent a pet");
    }

    private static <T> ResourceId fallback(Registry<T> registry, long seed) {
        List<ResourceId> available = registry.keySet().stream()
                .map(Identifier::toString)
                .map(ResourceId::parse)
                .toList();
        return DeterministicVariantFallback.select(available, seed);
    }

    private record VariantApplication(ResourceId effectiveId, boolean repaired) {
    }
}
