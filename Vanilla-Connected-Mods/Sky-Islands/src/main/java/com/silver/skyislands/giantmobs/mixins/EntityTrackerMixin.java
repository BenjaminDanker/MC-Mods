package com.silver.skyislands.giantmobs.mixins;

import com.silver.skyislands.giantmobs.GiantMobManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ServerChunkCache$EntityTracker")
public abstract class EntityTrackerMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<PlayerAssociatedNetworkHandler> listeners;
    @Shadow @Final private EntityTrackerEntry entry;

    @Shadow public abstract void stopTracking(ServerPlayer player);

    @Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void skyIslands$forceGiantTracking(ServerPlayer player, CallbackInfo ci) {
        if (!GiantMobManager.shouldForceTrack(this.entity)) {
            return;
        }

        if (player.level() != this.entity.level()) {
            this.stopTracking(player);
            ci.cancel();
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 entityPos = this.entity.position();
        double distanceSq = playerPos.distanceToSqr(entityPos);
        double maxDistance = 2032.0;
        double maxDistanceSq = maxDistance * maxDistance;

        if (distanceSq <= maxDistanceSq) {
            if (this.listeners.add(player.connection)) {
                this.entry.startTracking(player);
                ((ServerLevel) this.entity.level()).getSubscriptionTracker().trackEntity(this.entity);
                ((ServerLevel) this.entity.level()).getSubscriptionTracker().sendInitialIfSubscribed(player, this.entity);
            }
        } else {
            this.stopTracking(player);
        }

        ci.cancel();
    }
}