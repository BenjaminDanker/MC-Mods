package com.silver.witherfight.mixin;

import com.silver.witherfight.beacon.BeaconRedirector;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
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
    private World world;

    @Inject(method = "tick", at = @At("HEAD"))
    private void witherfight$redirectOnBeacon(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        if (!(this.world instanceof ServerWorld serverWorld)) {
            return;
        }

        BeaconRedirector.handlePlayer(player, serverWorld);
    }
}
