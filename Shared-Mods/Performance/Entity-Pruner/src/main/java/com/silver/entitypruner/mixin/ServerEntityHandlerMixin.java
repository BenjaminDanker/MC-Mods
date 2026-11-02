package com.silver.entitypruner.mixin;

import com.silver.entitypruner.EntityCountAccessor;
import com.silver.entitypruner.EntityProtection;
import com.silver.entitypruner.EntityPrunerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public class ServerEntityHandlerMixin {
    @Shadow
    @Final
    private ServerLevel this$0;

    @Inject(method = "onCreated", at = @At("HEAD"))
    private void onCreate(Entity entity, CallbackInfo ci) {
        if (!EntityPrunerConfig.getInstance().enablePruning) {
            return;
        }

        EntityCountAccessor accessor = (EntityCountAccessor) this.this$0;
        long chunkKey = ChunkPos.pack(entity.blockPosition());
        if (entity instanceof Mob) {
            if (EntityProtection.isProtectedMob(entity)) {
                return;
            }
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

    @Inject(method = "onDestroyed", at = @At("HEAD"))
    private void onDestroy(Entity entity, CallbackInfo ci) {
        if (!EntityPrunerConfig.getInstance().enablePruning) {
            return;
        }

        EntityCountAccessor accessor = (EntityCountAccessor) this.this$0;
        long chunkKey = ChunkPos.pack(entity.blockPosition());
        if (entity instanceof Mob) {
            if (EntityProtection.isProtectedMob(entity)) {
                return;
            }
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
