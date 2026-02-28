package com.silver.viewextend.mixin;

import com.silver.viewextend.ViewExtendMod;
import com.silver.viewextend.ViewExtendService;
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
    private void viewextend$forcePlayerTracking(ServerPlayerEntity observer, CallbackInfo ci) {
        if (this.entity instanceof ServerPlayerEntity targetPlayer && observer != targetPlayer) {
            if (observer.getEntityWorld() != targetPlayer.getEntityWorld()) {
                this.stopTracking(observer);
                ci.cancel();
                return;
            }

            ViewExtendService service = ViewExtendMod.getService();
            if (service == null) {
                return; // Fall back to vanilla if service isn't loaded
            }

            // We calculate max tracking distance using the service.
            int normalDistance = observer.getEntityWorld().getServer().getPlayerManager().getViewDistance();
            int effectiveChunks = service.getEffectiveTotalDistance(observer, normalDistance);
            
            // Convert to blocks, we also add an anchor of chunks distance (e.g. effectiveChunks * 16)
            double maxDistance = effectiveChunks * 16.0;
            double maxDistanceSq = maxDistance * maxDistance;

            Vec3d observerPos = observer.getEntityPos();
            Vec3d targetPos = targetPlayer.getEntityPos();
            double distanceSq = observerPos.squaredDistanceTo(targetPos);

            if (distanceSq <= maxDistanceSq) {
                if (this.listeners.add(observer.networkHandler)) {
                    this.entry.startTracking(observer);

                    if (this.listeners.size() == 1) {
                        ((ServerWorld) this.entity.getEntityWorld()).getSubscriptionTracker().trackEntity(this.entity);
                    }
                    ((ServerWorld) this.entity.getEntityWorld()).getSubscriptionTracker().sendInitialIfSubscribed(observer, this.entity);
                }
            } else {
                this.stopTracking(observer);
            }

            // Cancel vanilla logic to prevent it untracking based on normal chunk rules.
            ci.cancel();
        }
    }
}
