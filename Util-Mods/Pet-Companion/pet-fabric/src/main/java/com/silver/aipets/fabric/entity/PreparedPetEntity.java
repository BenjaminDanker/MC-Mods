package com.silver.aipets.fabric.entity;

import com.silver.aipets.common.domain.ResourceId;
import net.minecraft.entity.passive.TameableEntity;

import java.util.Objects;

/** Fully configured but not yet spawned; authority must commit before spawnEntity is called. */
public record PreparedPetEntity(
        TameableEntity entity,
        ResourceId effectiveVariantId,
        boolean compatibilityRepairRequired) {
    public PreparedPetEntity {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(effectiveVariantId, "effectiveVariantId");
    }
}
