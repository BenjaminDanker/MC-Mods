package com.silver.atlantis.spawn.mixin;

import com.silver.atlantis.spawn.drop.SpecialItemConversionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @Inject(method = "markDirty", at = @At("TAIL"))
    private void atlantis$onMarkDirty(CallbackInfo ci) {
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            SpecialItemConversionManager.onInventoryMaybeChanged(serverPlayer);
        }
    }
}
