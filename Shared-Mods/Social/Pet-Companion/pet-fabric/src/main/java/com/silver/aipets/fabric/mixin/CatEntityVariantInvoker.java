package com.silver.aipets.fabric.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Cat.class)
public interface CatEntityVariantInvoker {
    @Invoker("setVariant")
    void aipets$setVariant(Holder<CatVariant> variant);
}
