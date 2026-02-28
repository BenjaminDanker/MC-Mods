package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
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
import java.util.UUID;
import java.util.HashSet;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class EntityTrackerMixin {

    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<PlayerAssociatedNetworkHandler> listeners;
    @Shadow @Final private EntityTrackerEntry entry;

    @Shadow public abstract void stopTracking(ServerPlayerEntity player);

    @Unique
    private final Set<UUID> skyislands$resyncedPlayers = new HashSet<>();

    @Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void skyislands$forceDragonTracking(ServerPlayerEntity player, CallbackInfo ci) {
        if (this.entity instanceof EnderDragonEntity dragon && EnderDragonManager.isManaged(dragon)) {
            if (player.getEntityWorld() != this.entity.getEntityWorld()) {
                this.stopTracking(player);
                ci.cancel();
                return;
            }

            // We want to force track the dragon if it's within a large distance, bypassing vanilla chunk tracking checks.
            Vec3d playerPos = player.getEntityPos();
            Vec3d dragonPos = dragon.getEntityPos();
            double distanceSq = playerPos.squaredDistanceTo(dragonPos);

            // We use a distance that covers the absolute max unsimulated view distance of View-Extend (127 chunks).
            // 127 chunks = 2032 blocks. 2032^2 = 4129024
            double maxDistance = 2032.0;
            double maxDistanceSq = maxDistance * maxDistance;

            if (distanceSq <= maxDistanceSq) {
                // Vanilla Ender Dragons teleported from extreme distances on the client
                // suffer from a bug where their segmentCircularBuffer gets completely
                // blown out with extreme geometry coordinates, causing them to be completely invisible
                // until they fully traverse a chunk or the buffer settles.
                // We fix this by force re-sending the spawn packet once they enter normal rendering bounds.
                boolean isClose = distanceSq <= 160.0 * 160.0;
                if (isClose && this.skyislands$resyncedPlayers.add(player.getUuid())) {
                    this.stopTracking(player);
                } else if (!isClose) {
                    this.skyislands$resyncedPlayers.remove(player.getUuid());
                }

                // Force track
                if (this.listeners.add(player.networkHandler)) {
                    this.entry.startTracking(player);

                    // Handle SubscriptionTracker (debug rendering)
                    if (this.listeners.size() == 1) {
                        ((ServerWorld) this.entity.getEntityWorld()).getSubscriptionTracker().trackEntity(this.entity);
                    }
                    ((ServerWorld) this.entity.getEntityWorld()).getSubscriptionTracker().sendInitialIfSubscribed(player, this.entity);
                }
            } else {
                // Force untrack
                this.stopTracking(player);
                this.skyislands$resyncedPlayers.remove(player.getUuid());
            }

            // Cancel vanilla logic so it doesn't immediately untrack the dragon due to chunk not being tracked
            ci.cancel();
        }
    }
}
