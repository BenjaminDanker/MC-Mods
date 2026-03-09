package com.silver.skyislands.giantmobs.mixins;

import com.silver.skyislands.giantmobs.GiantMobManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class EntityTrackerMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<PlayerAssociatedNetworkHandler> listeners;
    @Shadow @Final private EntityTrackerEntry entry;

    @Shadow public abstract void stopTracking(ServerPlayerEntity player);

    @Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void skyIslands$forceGiantTracking(ServerPlayerEntity player, CallbackInfo ci) {
        if (!GiantMobManager.shouldForceTrack(this.entity)) {
            return;
        }

        if (player.getEntityWorld() != this.entity.getEntityWorld()) {
            this.stopTracking(player);
            ci.cancel();
            return;
        }

        Vec3d playerPos = player.getEntityPos();
        Vec3d entityPos = this.entity.getEntityPos();
        double distanceSq = playerPos.squaredDistanceTo(entityPos);
        double maxDistance = 2032.0;
        double maxDistanceSq = maxDistance * maxDistance;

        if (distanceSq <= maxDistanceSq) {
            if (this.listeners.add(player.networkHandler)) {
                this.entry.startTracking(player);
                ((ServerWorld) this.entity.getEntityWorld()).getSubscriptionTracker().trackEntity(this.entity);
                ((ServerWorld) this.entity.getEntityWorld()).getSubscriptionTracker().sendInitialIfSubscribed(player, this.entity);
            }
        } else {
            this.stopTracking(player);
        }

        ci.cancel();
    }
}