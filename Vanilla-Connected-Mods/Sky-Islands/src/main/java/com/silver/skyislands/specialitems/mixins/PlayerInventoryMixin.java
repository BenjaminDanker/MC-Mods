package com.silver.skyislands.specialitems.mixins;

import com.silver.skyislands.specialitems.SpecialItemConversionManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Inject(method = "setChanged", at = @At("TAIL"))
    private void skyIslands$onMarkDirty(CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            SpecialItemConversionManager.onInventoryMaybeChanged(serverPlayer);
        }
    }
}
