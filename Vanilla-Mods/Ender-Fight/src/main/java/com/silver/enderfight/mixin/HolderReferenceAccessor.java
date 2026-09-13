package com.silver.enderfight.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses the current Holder.Reference value while rebuilding the live dimension registry. */
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {
    @Invoker("bindValue")
    void enderfight$bindValue(T value);
}
