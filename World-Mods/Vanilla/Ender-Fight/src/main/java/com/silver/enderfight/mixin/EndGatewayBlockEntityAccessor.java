package com.silver.enderfight.mixin;

import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TheEndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessor {
    @Accessor("exactTeleport")
    boolean enderfight$isExactTeleport();
}
