package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Shadow
    public abstract void setStack(ItemStack stack);

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void soulbounditems$limitGroundSoulboundPickup(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemStack groundStack = getStack();
        if (!SoulboundRules.isSoulboundItem(groundStack)) {
            return;
        }

        int max = SoulboundRules.getSoulboundMaxOrUnlimited(serverPlayer);
        if (max == Integer.MAX_VALUE) {
            return;
        }

        int current = countCurrentSoulboundUnits(serverPlayer);
        int allowedToTake = max - current;
        if (allowedToTake <= 0) {
            ci.cancel();
            return;
        }

        if (groundStack.getCount() <= allowedToTake) {
            return;
        }

        ItemStack partial = groundStack.copy();
        partial.setCount(allowedToTake);

        int requested = partial.getCount();
        serverPlayer.getInventory().insertStack(partial);
        int inserted = requested - partial.getCount();

        if (inserted <= 0) {
            ci.cancel();
            return;
        }

        ItemStack entityStack = getStack();
        entityStack.decrement(inserted);
        if (entityStack.isEmpty()) {
            ((ItemEntity) (Object) this).discard();
        } else {
            setStack(entityStack);
        }

        serverPlayer.getInventory().markDirty();
        ci.cancel();
    }

    private static int countCurrentSoulboundUnits(ServerPlayerEntity player) {
        int[] counts = SoulboundRules.snapshotSoulboundCounts(player);
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        return total;
    }
}
