package com.silver.aipets.fabric.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Wolf.class)
public interface WolfEntityVariantInvoker {
    @Invoker("getVariant")
    Holder<WolfVariant> aipets$getVariant();

    @Invoker("setVariant")
    void aipets$setVariant(Holder<WolfVariant> variant);
}
