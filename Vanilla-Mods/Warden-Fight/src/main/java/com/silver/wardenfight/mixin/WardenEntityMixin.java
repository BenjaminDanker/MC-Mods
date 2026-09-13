package com.silver.wardenfight.mixin;

import com.silver.wardenfight.WardenFightMod;
import com.silver.wardenfight.teleport.WardenRedirector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WardenEntityMixin extends Entity {

    protected WardenEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"))
    private void wardenfight$onDeath(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof Warden warden)) {
            return;
        }

        WardenFightMod.LOGGER.info("Warden {} died; checking for redirect targets", warden.getUUID());
        Level world = warden.level();
        if (!(world instanceof ServerLevel serverWorld)) {
            WardenFightMod.LOGGER.debug("Warden death occurred on non-server world {}; skipping redirect", world.dimension().identifier());
            return;
        }

        WardenFightMod.LOGGER.info("Warden death confirmed on server world {}; invoking redirector", serverWorld.dimension().identifier());
        WardenRedirector.handleWardenDeath(serverWorld, warden.blockPosition());
    }
}
