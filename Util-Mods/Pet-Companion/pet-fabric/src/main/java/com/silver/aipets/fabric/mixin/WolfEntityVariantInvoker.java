package com.silver.aipets.fabric.mixin;

import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WolfEntity.class)
public interface WolfEntityVariantInvoker {
    @Invoker("getVariant")
    RegistryEntry<WolfVariant> aipets$getVariant();

    @Invoker("setVariant")
    void aipets$setVariant(RegistryEntry<WolfVariant> variant);
}
