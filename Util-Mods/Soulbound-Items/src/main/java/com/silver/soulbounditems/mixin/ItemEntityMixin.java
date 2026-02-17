package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    public abstract net.minecraft.item.ItemStack getStack();

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void soulbounditems$preventHostileOverflowPickup(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        if (!SoulboundRules.isSoulboundItem(getStack())) {
            return;
        }

        if (!SoulboundRules.isAtOrOverLimit(serverPlayer)) {
            return;
        }

        ci.cancel();
    }
}
