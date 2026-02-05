package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(ServerWorld.class)
public abstract class ServerWorldDragonSpawnDebugMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerWorldDragonSpawnDebugMixin.class);
    private static final Set<UUID> LOGGED_UNMANAGED = new HashSet<>();

    @Inject(method = "spawnEntity", at = @At("HEAD"))
    private void skyIslands$debugDragonSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        if (!(entity instanceof EnderDragonEntity dragon)) {
            return;
        }
        if (EnderDragonManager.isManaged(dragon)) {
            return;
        }

        UUID uuid = dragon.getUuid();
        if (!LOGGED_UNMANAGED.add(uuid)) {
            return;
        }

        RuntimeException trace = new RuntimeException("Unmanaged EnderDragonEntity spawn trace");
        LOGGER.warn("[Sky-Islands][debug] ServerWorld.spawnEntity called for unmanaged dragon uuid={} pos=({}, {}, {}) tags={} dim={}",
                dragon.getUuidAsString(),
                Math.round(dragon.getX() * 10.0) / 10.0,
                Math.round(dragon.getY() * 10.0) / 10.0,
                Math.round(dragon.getZ() * 10.0) / 10.0,
                dragon.getCommandTags().size(),
                ((ServerWorld) (Object) this).getRegistryKey().getValue(),
                trace);
    }
}
