package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {

    @Shadow
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$protectBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnProtectionManager.INSTANCE.shouldBlockBreak(player, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$protectPlace(
        ServerPlayer player,
        Level world,
        ItemStack stack,
        InteractionHand hand,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!(world instanceof ServerLevel)) {
            return;
        }

        Item item = stack.getItem();
        if (!spawnprotect$isPotentialPlacementItem(item)) {
            return;
        }

        BlockPos hitPos = hitResult.getBlockPos();

        if (SpawnProtectionManager.INSTANCE.shouldBlockPlace(player, hitPos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        BlockPos offsetPos = hitPos.relative(hitResult.getDirection());
        if (SpawnProtectionManager.INSTANCE.shouldBlockPlace(player, offsetPos)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    private static boolean spawnprotect$isPotentialPlacementItem(Item item) {
        if (item instanceof BlockItem) {
            return true;
        }

        if (item instanceof BucketItem) {
            return true;
        }

        return (item instanceof FlintAndSteelItem) || (item instanceof FireChargeItem);
    }
}
