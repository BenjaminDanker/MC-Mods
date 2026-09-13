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

/** Extends player-to-player tracking to View-Extend's effective distance. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class EntityTrackerMixin {
    @Shadow @Final private ServerEntity serverEntity;
    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Shadow public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void viewextend$updatePlayer(ServerPlayer observer, CallbackInfo ci) {
        if (!(this.entity instanceof ServerPlayer targetPlayer) || observer == targetPlayer) {
            return;
        }

        if (observer.level() != targetPlayer.level()) {
            this.removePlayer(observer);
            ci.cancel();
            return;
        }

        ViewExtendService service = ViewExtendMod.getService();
        if (service == null) {
            return;
        }

        ServerLevel world = (ServerLevel) observer.level();
        int normalDistance = world.getServer().getPlayerList().getViewDistance();
        int effectiveChunks = service.getEffectiveTotalDistance(observer, normalDistance);
        double maxDistance = effectiveChunks * 16.0;
        Vec3 delta = observer.position().subtract(targetPlayer.position());
        boolean shouldTrack = delta.x * delta.x + delta.z * delta.z <= maxDistance * maxDistance
            && this.entity.broadcastToPlayer(observer);

        if (shouldTrack) {
            if (this.seenBy.add(observer.connection)) {
                this.serverEntity.addPairing(observer);
                world.debugSynchronizers().startTrackingEntity(observer, this.entity);
            }
        } else {
            this.removePlayer(observer);
        }

        // Prevent the vanilla chunk-distance check from undoing extended tracking.
        ci.cancel();
    }
}
