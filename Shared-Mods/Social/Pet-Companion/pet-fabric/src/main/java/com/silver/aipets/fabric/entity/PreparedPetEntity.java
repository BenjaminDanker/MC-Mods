package com.silver.aipets.fabric.entity;

import com.silver.aipets.common.domain.ResourceId;
import java.util.Objects;
import net.minecraft.world.entity.TamableAnimal;

/** Fully configured but not yet spawned; authority must commit before spawnEntity is called. */
public record PreparedPetEntity(
        TamableAnimal entity,
        ResourceId effectiveVariantId,
        boolean compatibilityRepairRequired) {
    public PreparedPetEntity {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(effectiveVariantId, "effectiveVariantId");
    }
}
