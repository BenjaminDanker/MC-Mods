package com.silver.wardenfight.mixin;

import com.silver.wardenfight.WardenFightMod;
import com.silver.wardenfight.teleport.WardenRedirector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WardenEntityMixin extends Entity {

    protected WardenEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void wardenfight$onDeath(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof WardenEntity warden)) {
            return;
        }

        WardenFightMod.LOGGER.info("Warden {} died; checking for redirect targets", warden.getUuid());
        World world = warden.getEntityWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            WardenFightMod.LOGGER.debug("Warden death occurred on non-server world {}; skipping redirect", world.getRegistryKey().getValue());
            return;
        }

        WardenFightMod.LOGGER.info("Warden death confirmed on server world {}; invoking redirector", serverWorld.getRegistryKey().getValue());
        WardenRedirector.handleWardenDeath(serverWorld, warden.getBlockPos());
    }
}
