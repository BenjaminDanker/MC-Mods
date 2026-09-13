package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemDragonHeadPlaceMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void skyislands$trackDragonHeadPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context == null) {
            return;
        }

        InteractionResult result = cir.getReturnValue();
        if (result == null || !result.consumesAction()) {
            return;
        }

        Level world = context.getLevel();
        if (world == null || world.isClientSide()) {
            return;
        }

        BlockPos pos = context.getClickedPos();
        EnderDragonManager.onPossibleDragonHeadPlaced(world, pos);
    }
}
