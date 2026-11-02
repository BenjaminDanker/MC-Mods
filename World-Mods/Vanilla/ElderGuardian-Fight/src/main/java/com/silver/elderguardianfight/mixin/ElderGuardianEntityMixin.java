package com.silver.elderguardianfight.mixin;

import com.silver.elderguardianfight.ElderGuardianFightMod;
import com.silver.elderguardianfight.teleport.GuardianRedirector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ElderGuardianEntityMixin extends Entity {

    protected ElderGuardianEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"))
    private void elderguardianfight$onDeath(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof ElderGuardian guardian)) {
            return;
        }

        ElderGuardianFightMod.LOGGER.info("Elder Guardian {} died; checking for redirect targets", guardian.getUUID());
        Level world = guardian.level();
        if (!(world instanceof ServerLevel serverWorld)) {
            ElderGuardianFightMod.LOGGER.debug("Guardian death occurred on non-server world {}; skipping redirect", world.dimension().identifier());
            return;
        }

        ElderGuardianFightMod.LOGGER.info("Guardian death confirmed on server world {}; invoking redirector", serverWorld.dimension().identifier());
        GuardianRedirector.handleGuardianDeath(serverWorld, guardian.blockPosition());
    }
}
