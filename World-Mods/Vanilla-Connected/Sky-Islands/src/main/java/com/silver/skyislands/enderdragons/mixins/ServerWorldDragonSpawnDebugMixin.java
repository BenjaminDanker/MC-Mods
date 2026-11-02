package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.server.level.ServerLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(ServerLevel.class)
public abstract class ServerWorldDragonSpawnDebugMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerWorldDragonSpawnDebugMixin.class);
    private static final Set<UUID> LOGGED_UNMANAGED = new HashSet<>();

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void skyIslands$debugDragonSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        if (!(entity instanceof EnderDragon dragon)) {
            return;
        }
        if (EnderDragonManager.isManaged(dragon)) {
            return;
        }

        UUID uuid = dragon.getUUID();
        if (!LOGGED_UNMANAGED.add(uuid)) {
            return;
        }

        RuntimeException trace = new RuntimeException("Unmanaged EnderDragon spawn trace");
        LOGGER.warn("[Sky-Islands][debug] ServerLevel.addFreshEntity called for unmanaged dragon uuid={} pos=({}, {}, {}) tags={} dim={}",
                dragon.getStringUUID(),
                Math.round(dragon.getX() * 10.0) / 10.0,
                Math.round(dragon.getY() * 10.0) / 10.0,
                Math.round(dragon.getZ() * 10.0) / 10.0,
                dragon.entityTags().size(),
                ((ServerLevel) (Object) this).dimension().identifier(),
                trace);
    }
}
