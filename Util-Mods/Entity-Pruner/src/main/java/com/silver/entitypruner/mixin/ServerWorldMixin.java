package com.silver.entitypruner.mixin;

import com.silver.entitypruner.EntityCountAccessor;
import com.silver.entitypruner.EntityPruner;
import com.silver.entitypruner.EntityPrunerConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public class ServerWorldMixin implements EntityCountAccessor {
    @Unique
    private Long2IntOpenHashMap mobCountsByChunk = new Long2IntOpenHashMap();
    @Unique
    private Long2IntOpenHashMap itemCountsByChunk = new Long2IntOpenHashMap();
    @Unique
    private int tickCounter = 0;
    @Unique
    private boolean countersInitialized = false;
    @Unique
    private boolean resyncInProgress = false;
    @Unique
    private Long2IntOpenHashMap resyncMobCountsByChunk;
    @Unique
    private Long2IntOpenHashMap resyncItemCountsByChunk;
    @Unique
    private Long2IntOpenHashMap resyncMobDeltaByChunk;
    @Unique
    private Long2IntOpenHashMap resyncItemDeltaByChunk;
    @Unique
    private Iterator<Entity> resyncIterator;

    @Unique
    private boolean pruneInProgress = false;

    @Unique
    private Iterator<Entity> pruneIterator;

    @Unique
    private int blockedMobSpawnsSinceLog = 0;

    @Unique
    private int prunedItemsSinceLog = 0;

    @Unique
    private long nextPruneLogTime = 0;

    @Override
    public int getMobCount(long chunkKey) {
        return this.mobCountsByChunk.get(chunkKey);
    }

    @Override
    public int getItemCount(long chunkKey) {
        return this.itemCountsByChunk.get(chunkKey);
    }

    @Override
    public void incrementMobCount(long chunkKey) {
        this.mobCountsByChunk.addTo(chunkKey, 1);
    }

    @Override
    public void decrementMobCount(long chunkKey) {
        int next = this.mobCountsByChunk.addTo(chunkKey, -1);
        if (next <= 0) {
            this.mobCountsByChunk.remove(chunkKey);
        }
    }

    @Override
    public void incrementItemCount(long chunkKey) {
        this.itemCountsByChunk.addTo(chunkKey, 1);
    }

    @Override
    public void decrementItemCount(long chunkKey) {
        int next = this.itemCountsByChunk.addTo(chunkKey, -1);
        if (next <= 0) {
            this.itemCountsByChunk.remove(chunkKey);
        }
    }

    @Override
    public boolean isResyncInProgress() {
        return this.resyncInProgress;
    }

    @Override
    public void incrementResyncMobDelta(long chunkKey) {
        if (this.resyncMobDeltaByChunk != null) {
            this.resyncMobDeltaByChunk.addTo(chunkKey, 1);
        }
    }

    @Override
    public void decrementResyncMobDelta(long chunkKey) {
        if (this.resyncMobDeltaByChunk == null) {
            return;
        }

        int next = this.resyncMobDeltaByChunk.addTo(chunkKey, -1);
        if (next == 0) {
            this.resyncMobDeltaByChunk.remove(chunkKey);
        }
    }

    @Override
    public void incrementResyncItemDelta(long chunkKey) {
        if (this.resyncItemDeltaByChunk != null) {
            this.resyncItemDeltaByChunk.addTo(chunkKey, 1);
        }
    }

    @Override
    public void decrementResyncItemDelta(long chunkKey) {
        if (this.resyncItemDeltaByChunk == null) {
            return;
        }

        int next = this.resyncItemDeltaByChunk.addTo(chunkKey, -1);
        if (next == 0) {
            this.resyncItemDeltaByChunk.remove(chunkKey);
        }
    }

    @Unique
    private static long getChunkKey(Entity entity) {
        ChunkPos pos = new ChunkPos(entity.getBlockPos());
        return pos.toLong();
    }

    @Unique
    private static void applyDeltaMap(Long2IntOpenHashMap baseMap, Long2IntOpenHashMap deltaMap) {
        if (baseMap == null || deltaMap == null || deltaMap.isEmpty()) {
            return;
        }

        for (long key : deltaMap.keySet()) {
            int delta = deltaMap.get(key);
            if (delta == 0) {
                continue;
            }

            int next = baseMap.addTo(key, delta);
            if (next <= 0) {
                baseMap.remove(key);
            }
        }
    }

    @Unique
    private void beginResync(ServerWorld world) {
        this.resyncIterator = world.iterateEntities().iterator();
        this.resyncMobCountsByChunk = new Long2IntOpenHashMap();
        this.resyncItemCountsByChunk = new Long2IntOpenHashMap();
        this.resyncMobDeltaByChunk = new Long2IntOpenHashMap();
        this.resyncItemDeltaByChunk = new Long2IntOpenHashMap();
        this.resyncInProgress = true;
    }

    @Unique
    private void continueResync() {
        if (!this.resyncInProgress || this.resyncIterator == null) {
            return;
        }

        EntityPrunerConfig cfg = EntityPrunerConfig.getInstance();
        int maxPerTick = Math.max(50, cfg.resyncMaxEntitiesPerTick);
        int processed = 0;

        while (processed < maxPerTick && this.resyncIterator.hasNext()) {
            Entity entity = this.resyncIterator.next();
            long chunkKey = getChunkKey(entity);
            if (entity instanceof MobEntity) {
                this.resyncMobCountsByChunk.addTo(chunkKey, 1);
            } else if (entity instanceof ItemEntity) {
                this.resyncItemCountsByChunk.addTo(chunkKey, 1);
            }
            processed++;
        }

        if (!this.resyncIterator.hasNext()) {
            applyDeltaMap(this.resyncMobCountsByChunk, this.resyncMobDeltaByChunk);
            applyDeltaMap(this.resyncItemCountsByChunk, this.resyncItemDeltaByChunk);

            this.mobCountsByChunk = this.resyncMobCountsByChunk;
            this.itemCountsByChunk = this.resyncItemCountsByChunk;

            this.countersInitialized = true;

            this.resyncIterator = null;
            this.resyncInProgress = false;

            this.resyncMobCountsByChunk = null;
            this.resyncItemCountsByChunk = null;
            this.resyncMobDeltaByChunk = null;
            this.resyncItemDeltaByChunk = null;
        }
    }

    @Unique
    private boolean hasOverfullItemChunk(EntityPrunerConfig cfg) {
        if (cfg.maxItemsPerChunk <= 0) {
            return false;
        }

        IntIterator iter = this.itemCountsByChunk.values().iterator();
        while (iter.hasNext()) {
            if (iter.nextInt() > cfg.maxItemsPerChunk) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void beginPrune(ServerWorld world) {
        this.pruneIterator = world.iterateEntities().iterator();
        this.pruneInProgress = true;
    }

    @Unique
    private void continuePrune(ServerWorld world, EntityPrunerConfig cfg) {
        if (!this.pruneInProgress || this.pruneIterator == null) {
            return;
        }

        if (!hasOverfullItemChunk(cfg)) {
            this.pruneIterator = null;
            this.pruneInProgress = false;
            return;
        }

        int delayTicks = 20 * Math.max(0, cfg.itemPruneDelaySeconds);
        int maxRemovals = Math.max(1, cfg.itemPruneMaxRemovalsPerTick);
        int scanLimit = Math.max(50, cfg.resyncMaxEntitiesPerTick);

        int removed = 0;
        int scanned = 0;

        while (removed < maxRemovals && scanned < scanLimit && this.pruneIterator.hasNext()) {
            Entity entity = this.pruneIterator.next();
            scanned++;

            if (!(entity instanceof ItemEntity itemEntity)) {
                continue;
            }

            if (delayTicks > 0 && itemEntity.getItemAge() < delayTicks) {
                continue;
            }

            net.minecraft.item.ItemStack stack = itemEntity.getStack();
            net.minecraft.component.type.NbtComponent customData = stack.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            if (customData != null && !customData.isEmpty()) {
                continue;
            }

            long chunkKey = getChunkKey(itemEntity);
            if (this.getItemCount(chunkKey) <= cfg.maxItemsPerChunk) {
                continue;
            }

            itemEntity.discard();
            removed++;
            this.prunedItemsSinceLog++;
        }

        if (!this.pruneIterator.hasNext()) {
            this.pruneIterator = null;
            this.pruneInProgress = false;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        EntityPrunerConfig cfg = EntityPrunerConfig.getInstance();
        if (!cfg.enablePruning) return;

        ServerWorld world = (ServerWorld) (Object) this;
        int intervalTicks = 20 * Math.max(5, cfg.resyncIntervalSeconds);

        if (!this.countersInitialized) {
            if (!this.resyncInProgress) {
                beginResync(world);
            }
            continueResync();
            return;
        }

        if (!this.resyncInProgress && this.tickCounter++ % intervalTicks == 0) {
            beginResync(world);
        }

        if (this.resyncInProgress) {
            continueResync();
        }

        if (this.countersInitialized && !this.resyncInProgress && cfg.maxItemsPerChunk > 0) {
            if (!this.pruneInProgress && hasOverfullItemChunk(cfg)) {
                beginPrune(world);
            }
            if (this.pruneInProgress) {
                continuePrune(world, cfg);
            }
        }

        if (this.countersInitialized && cfg.enablePruneLogging) {
            long now = world.getTime();
            if (this.nextPruneLogTime <= 0) {
                this.nextPruneLogTime = now + 20L * Math.max(1, cfg.pruneLogIntervalSeconds);
            } else if (now >= this.nextPruneLogTime) {
                if (this.blockedMobSpawnsSinceLog > 0 || this.prunedItemsSinceLog > 0) {
                    EntityPruner.LOGGER.info(
                        "EntityPruner: blockedMobSpawns={}, prunedItems={} (last {}s) in {}",
                        this.blockedMobSpawnsSinceLog,
                        this.prunedItemsSinceLog,
                        Math.max(1, cfg.pruneLogIntervalSeconds),
                        world.getRegistryKey().getValue()
                    );
                }
                this.blockedMobSpawnsSinceLog = 0;
                this.prunedItemsSinceLog = 0;
                this.nextPruneLogTime = now + 20L * Math.max(1, cfg.pruneLogIntervalSeconds);
            }
        }
    }
    
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        EntityPrunerConfig cfg = EntityPrunerConfig.getInstance();
        if (!cfg.enablePruning) return;

        if (!this.countersInitialized) {
            return;
        }
        
        if (entity instanceof MobEntity) {
            if (entity.getCommandTags().contains("no_despawn") || entity.hasCustomName()) {
                return;
            }
            long chunkKey = getChunkKey(entity);
            if (this.getMobCount(chunkKey) >= cfg.maxMobsPerChunk) {
                this.blockedMobSpawnsSinceLog++;
                cir.setReturnValue(false);
                return;
            }
        } else if (entity instanceof ItemEntity itemEntity) {
            net.minecraft.item.ItemStack stack = itemEntity.getStack();
            net.minecraft.component.type.NbtComponent customData = stack.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            if (customData != null && !customData.isEmpty()) {
                return;
            }
            // Items are not blocked at spawn time; if a chunk is over cap, we prune them gradually in tick after a delay.
        }
    }
}