package com.silver.enderfight.mixin;

import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.EndGatewayFeatureConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityInvoker {
    @Invoker("setupExitPortalLocation")
    static BlockPos enderfight$setupExitPortalLocation(ServerWorld world, BlockPos pos) {
        throw new AssertionError("Mixin failed to apply");
    }

    @Invoker("createPortal")
    static void enderfight$createPortal(ServerWorld world, BlockPos pos, EndGatewayFeatureConfig config) {
        throw new AssertionError("Mixin failed to apply");
    }
}
