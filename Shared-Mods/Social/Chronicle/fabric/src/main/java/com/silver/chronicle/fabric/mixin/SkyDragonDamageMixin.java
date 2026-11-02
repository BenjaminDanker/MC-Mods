package com.silver.chronicle.fabric.mixin;

import com.silver.chronicle.fabric.ChronicleEvents;
import com.silver.chronicle.fabric.ChronicleMod;
import com.silver.chronicle.common.ChronicleEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderDragon.class)
abstract class SkyDragonDamageMixin {
    @Unique private boolean chronicle$wasDyingBeforeHit;
    @Unique private boolean chronicle$reportedDyingTransition;

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
    private void chronicle$capturePriorPhase(ServerLevel world, EnderDragonPart part, DamageSource source,
                                             float amount, CallbackInfoReturnable<Boolean> cir) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        chronicle$wasDyingBeforeHit = dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
    private void chronicle$recordLethalPhaseTransition(ServerLevel world, EnderDragonPart part, DamageSource source,
                                                       float amount, CallbackInfoReturnable<Boolean> cir) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (chronicle$reportedDyingTransition || chronicle$wasDyingBeforeHit || !cir.getReturnValueZ()
                || !dragon.entityTags().contains("sky_islands_managed_dragon")
                || dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.DYING) return;
        chronicle$reportedDyingTransition = true;
        var player = ChronicleEvents.resolvePlayer(dragon, source);
        if (player != null) ChronicleMod.complete(player, ChronicleEvent.FIRST_DEFEAT_SKY_DRAGON.id());
    }
}
