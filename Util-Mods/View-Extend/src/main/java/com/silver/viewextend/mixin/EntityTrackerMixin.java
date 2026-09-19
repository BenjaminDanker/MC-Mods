package com.silver.viewextend.mixin;

import com.silver.viewextend.ViewExtendMod;
import com.silver.viewextend.ViewExtendService;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends players and Sky-Island managed dragons without creating entity/chunk tickets. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class EntityTrackerMixin implements com.silver.viewextend.ExtendedPlayerTracker {
    @Override
    public boolean viewextend$eligible() {
        return entity instanceof ServerPlayer
                || (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                    && entity.entityTags().contains("sky_islands_managed_dragon"));
    }
    @Shadow public abstract void updatePlayer(ServerPlayer player);

    @Override
    public void viewextend$refresh(ServerPlayer observer) { this.updatePlayer(observer); }
    @Shadow @Final private ServerEntity serverEntity;
    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Shadow public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void viewextend$updatePlayer(ServerPlayer observer, CallbackInfo ci) {
        if (!viewextend$eligible() || observer == this.entity) {
            return;
        }

        if (observer.level() != this.entity.level()) {
            this.removePlayer(observer);
            ci.cancel();
            return;
        }

        ViewExtendService service = ViewExtendMod.getService();
        if (service == null || !service.isEnabled()) {
            return;
        }

        ServerLevel world = (ServerLevel) observer.level();
        int normalDistance = world.getServer().getPlayerList().getViewDistance();
        int effectiveChunks = service.getEffectiveTotalDistance(observer, normalDistance);
        Vec3 delta = observer.position().subtract(this.entity.position());
        double distanceSquared = delta.x * delta.x + delta.z * delta.z;

        // Keep vanilla's entity range, visibility, and tracked-chunk checks in the simulated area.
        if (this.entity instanceof ServerPlayer && observer.getChunkTrackingView().contains(this.entity.chunkPosition())) {
            return;
        }

        double maxDistance = effectiveChunks * 16.0;
        boolean shouldTrack = distanceSquared <= maxDistance * maxDistance
            && service.hasSentVisualChunk(observer, this.entity.chunkPosition())
            && this.entity.broadcastToPlayer(observer);

        if (shouldTrack) {
            if (this.seenBy.add(observer.connection)) {
                this.serverEntity.addPairing(observer);
                if (this.seenBy.size() == 1) {
                    world.debugSynchronizers().registerEntity(this.entity);
                }
                world.debugSynchronizers().startTrackingEntity(observer, this.entity);
            }
        } else {
            this.removePlayer(observer);
        }

        // Vanilla cannot see synthetic chunks, so handle only the extended area here.
        ci.cancel();
    }
}
