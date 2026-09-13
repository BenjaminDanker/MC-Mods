package com.silver.witherfight.mixin;

import com.silver.witherfight.beacon.BeaconRedirector;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the player tick loop to detect when someone is standing on an active beacon.
 */
@Mixin(Entity.class)
abstract class BeaconTeleportMixin {
    @Shadow
    private Level level;

    @Inject(method = "tick", at = @At("HEAD"))
    private void witherfight$redirectOnBeacon(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (!(this.level instanceof ServerLevel serverWorld)) {
            return;
        }

        BeaconRedirector.handlePlayer(player, serverWorld);
    }
}
