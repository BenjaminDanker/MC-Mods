package com.silver.aipets.fabric.entity;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.ResourceId;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.mixin.CatEntityVariantInvoker;
import com.silver.aipets.fabric.mixin.WolfEntityVariantInvoker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Creates an in-memory vanilla representation without spawning or writing authority. */
public final class PetEntityFactory {
    public PreparedPetEntity prepare(
            ServerWorld world,
            Pet pet,
            UUID entityUuid,
            WorldPosition position,
            boolean sleeping) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(position, "position");

        TameableEntity entity = createVanillaEntity(world, pet.appearance().species());
        entity.setUuid(entityUuid);
        entity.refreshPositionAndAngles(position.x(), position.y(), position.z(), 0.0F, 0.0F);
        entity.setBreedingAge(0);
        entity.setCustomName(Text.literal(pet.name()));
        entity.setCustomNameVisible(true);

        VariantApplication variant = applyVariant(world, entity, pet);
        EntityAttributeInstance scale = entity.getAttributeInstance(EntityAttributes.SCALE);
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

    private static TameableEntity createVanillaEntity(ServerWorld world, PetSpecies species) {
        TameableEntity entity = switch (species) {
            case CAT -> EntityType.CAT.create(world, SpawnReason.COMMAND);
            case DOG -> EntityType.WOLF.create(world, SpawnReason.COMMAND);
        };
        if (entity == null) {
            throw new IllegalStateException("Minecraft failed to construct " + species + " entity");
        }
        return entity;
    }

    private static VariantApplication applyVariant(
            ServerWorld world, TameableEntity entity, Pet pet) {
        Identifier requested = Identifier.of(pet.appearance().variantId().value());
        long seed = pet.appearance().appearanceSeed().orElse(
                pet.petId().getMostSignificantBits() ^ pet.petId().getLeastSignificantBits());

        if (entity instanceof CatEntity cat) {
            Registry<CatVariant> registry = world.getRegistryManager().getOrThrow(RegistryKeys.CAT_VARIANT);
            RegistryEntry.Reference<CatVariant> entry = registry.getEntry(requested).orElse(null);
            boolean repaired = entry == null;
            ResourceId effective = repaired ? fallback(registry, seed) : ResourceId.parse(requested.toString());
            if (entry == null) {
                entry = registry.getEntry(Identifier.of(effective.value())).orElseThrow();
            }
            ((CatEntityVariantInvoker) cat).aipets$setVariant(entry);
            return new VariantApplication(effective, repaired);
        }
        if (entity instanceof WolfEntity wolf) {
            Registry<WolfVariant> registry = world.getRegistryManager().getOrThrow(RegistryKeys.WOLF_VARIANT);
            RegistryEntry.Reference<WolfVariant> entry = registry.getEntry(requested).orElse(null);
            boolean repaired = entry == null;
            ResourceId effective = repaired ? fallback(registry, seed) : ResourceId.parse(requested.toString());
            if (entry == null) {
                entry = registry.getEntry(Identifier.of(effective.value())).orElseThrow();
            }
            ((WolfEntityVariantInvoker) wolf).aipets$setVariant(entry);
            return new VariantApplication(effective, repaired);
        }
        throw new IllegalArgumentException("Only cat/wolf entities can represent a pet");
    }

    private static <T> ResourceId fallback(Registry<T> registry, long seed) {
        List<ResourceId> available = registry.getIds().stream()
                .map(Identifier::toString)
                .map(ResourceId::parse)
                .toList();
        return DeterministicVariantFallback.select(available, seed);
    }

    private record VariantApplication(ResourceId effectiveId, boolean repaired) {
    }
}
