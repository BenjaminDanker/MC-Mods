package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    public abstract void setItem(ItemStack stack);

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void soulbounditems$limitGroundSoulboundPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack groundStack = getItem();
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
        serverPlayer.getInventory().add(partial);
        int inserted = requested - partial.getCount();

        if (inserted <= 0) {
            ci.cancel();
            return;
        }

        ItemStack entityStack = getItem();
        entityStack.shrink(inserted);
        if (entityStack.isEmpty()) {
            ((ItemEntity) (Object) this).discard();
        } else {
            setItem(entityStack);
        }

        serverPlayer.getInventory().setChanged();
        ci.cancel();
    }

    private static int countCurrentSoulboundUnits(ServerPlayer player) {
        int[] counts = SoulboundRules.snapshotSoulboundCounts(player);
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        return total;
    }
}
