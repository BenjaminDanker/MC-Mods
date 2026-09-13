package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.protect.ProtectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void atlantis$protectBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ProtectionManager.INSTANCE.shouldBlockBreak(player, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void atlantis$protectPlace(
        ServerPlayer player,
        Level world,
        ItemStack stack,
        InteractionHand hand,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }

        Item item = stack.getItem();
        if (!atlantis$isPotentialPlacementItem(item)) {
            return;
        }

        BlockPos hitPos = hitResult.getBlockPos();
        BlockPos offsetPos = hitPos.relative(hitResult.getDirection());

        // Block placement if the final placement target would be inside protected interior.
        // We conservatively check both hitPos (replaceable blocks) and offsetPos (normal placement).
        if (ProtectionManager.INSTANCE.shouldBlockPlace(player, hitPos)
            || ProtectionManager.INSTANCE.shouldBlockPlace(player, offsetPos)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    private static boolean atlantis$isPotentialPlacementItem(Item item) {
        // Block items
        if (item instanceof BlockItem) {
            return true;
        }

        // Fluids (buckets)
        if (item instanceof BucketItem) {
            return true;
        }

        // Fire-starting items
        return (item instanceof FlintAndSteelItem) || (item instanceof FireChargeItem);
    }
}
