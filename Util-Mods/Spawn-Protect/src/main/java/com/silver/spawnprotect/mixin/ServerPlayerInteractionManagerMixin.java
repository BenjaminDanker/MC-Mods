package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$protectBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnProtectionManager.INSTANCE.shouldBlockBreak(player, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$protectPlace(
        ServerPlayerEntity player,
        World world,
        ItemStack stack,
        Hand hand,
        BlockHitResult hitResult,
        CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!(world instanceof ServerWorld)) {
            return;
        }

        Item item = stack.getItem();
        if (!isPotentialPlacementItem(item)) {
            return;
        }

        BlockPos hitPos = hitResult.getBlockPos();

        if (SpawnProtectionManager.INSTANCE.shouldBlockPlace(player, hitPos)) {
            cir.setReturnValue(ActionResult.FAIL);
            return;
        }

        BlockPos offsetPos = hitPos.offset(hitResult.getSide());
        if (SpawnProtectionManager.INSTANCE.shouldBlockPlace(player, offsetPos)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    private static boolean isPotentialPlacementItem(Item item) {
        if (item instanceof BlockItem) {
            return true;
        }

        if (item instanceof BucketItem) {
            return true;
        }

        return (item instanceof FlintAndSteelItem) || (item instanceof FireChargeItem);
    }
}
