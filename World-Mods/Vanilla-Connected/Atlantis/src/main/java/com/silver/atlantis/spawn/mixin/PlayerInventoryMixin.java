package com.silver.atlantis.spawn.mixin;

import com.silver.atlantis.spawn.drop.SpecialItemConversionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
    private void atlantis$onMarkDirty(CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            SpecialItemConversionManager.onInventoryMaybeChanged(serverPlayer);
        }
    }
}
