package com.silver.enderfight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TheEndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityInvoker {
    @Invoker("findOrCreateValidTeleportPos")
    static BlockPos enderfight$setupExitPortalLocation(ServerLevel world, BlockPos pos) {
        throw new AssertionError("Mixin failed to apply");
    }

    @Invoker("spawnGatewayPortal")
    static void enderfight$createPortal(ServerLevel world, BlockPos pos, EndGatewayConfiguration config) {
        throw new AssertionError("Mixin failed to apply");
    }
}
