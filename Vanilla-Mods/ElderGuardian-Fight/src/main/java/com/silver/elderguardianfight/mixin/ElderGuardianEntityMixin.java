package com.silver.elderguardianfight.mixin;

import com.silver.elderguardianfight.ElderGuardianFightMod;
import com.silver.elderguardianfight.teleport.GuardianRedirector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ElderGuardianEntityMixin extends Entity {

    protected ElderGuardianEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void elderguardianfight$onDeath(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof ElderGuardianEntity guardian)) {
            return;
        }

        ElderGuardianFightMod.LOGGER.info("Elder Guardian {} died; checking for redirect targets", guardian.getUuid());
        World world = guardian.getEntityWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            ElderGuardianFightMod.LOGGER.debug("Guardian death occurred on non-server world {}; skipping redirect", world.getRegistryKey().getValue());
            return;
        }

        ElderGuardianFightMod.LOGGER.info("Guardian death confirmed on server world {}; invoking redirector", serverWorld.getRegistryKey().getValue());
        GuardianRedirector.handleGuardianDeath(serverWorld, guardian.getBlockPos());
    }
}
