package com.silver.enderfight.mixin;

import net.minecraft.block.entity.EndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessor {
    @Accessor("exactTeleport")
    boolean enderfight$isExactTeleport();
}
