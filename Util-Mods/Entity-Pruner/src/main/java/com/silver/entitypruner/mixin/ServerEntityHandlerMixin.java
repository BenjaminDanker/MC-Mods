package com.silver.entitypruner.mixin;

import com.silver.entitypruner.EntityCountAccessor;
import com.silver.entitypruner.EntityPrunerConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.world.ServerWorld$ServerEntityHandler")
public class ServerEntityHandlerMixin {
    @Shadow
    @Final
    private ServerWorld field_26936;

    @Inject(method = "create", at = @At("HEAD"))
    private void onCreate(Entity entity, CallbackInfo ci) {
        if (!EntityPrunerConfig.getInstance().enablePruning) {
            return;
        }

        EntityCountAccessor accessor = (EntityCountAccessor) this.field_26936;
        long chunkKey = new ChunkPos(entity.getBlockPos()).toLong();
        if (entity instanceof MobEntity) {
            accessor.incrementMobCount(chunkKey);
            if (accessor.isResyncInProgress()) {
                accessor.incrementResyncMobDelta(chunkKey);
            }
        } else if (entity instanceof ItemEntity) {
            accessor.incrementItemCount(chunkKey);
            if (accessor.isResyncInProgress()) {
                accessor.incrementResyncItemDelta(chunkKey);
            }
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void onDestroy(Entity entity, CallbackInfo ci) {
        if (!EntityPrunerConfig.getInstance().enablePruning) {
            return;
        }

        EntityCountAccessor accessor = (EntityCountAccessor) this.field_26936;
        long chunkKey = new ChunkPos(entity.getBlockPos()).toLong();
        if (entity instanceof MobEntity) {
            accessor.decrementMobCount(chunkKey);
            if (accessor.isResyncInProgress()) {
                accessor.decrementResyncMobDelta(chunkKey);
            }
        } else if (entity instanceof ItemEntity) {
            accessor.decrementItemCount(chunkKey);
            if (accessor.isResyncInProgress()) {
                accessor.decrementResyncItemDelta(chunkKey);
            }
        }
    }
}
