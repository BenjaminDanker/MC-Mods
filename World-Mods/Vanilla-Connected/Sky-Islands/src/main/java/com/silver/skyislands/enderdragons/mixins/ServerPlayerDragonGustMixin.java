package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.DragonGustFallProtection;
import com.silver.skyislands.enderdragons.DragonGustProtectedAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDragonGustMixin implements DragonGustProtectedAccess {
    @Unique private long skyIslands$gustFallProtectionUntil=Long.MIN_VALUE;

    @Override
    public void skyIslands$protectFromGustFallDamage(long untilTick) {
        skyIslands$gustFallProtectionUntil=Math.max(skyIslands$gustFallProtectionUntil,untilTick);
    }

    @ModifyVariable(method="hurtServer",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float skyIslands$capGustFallDamage(float amount,ServerLevel world,DamageSource source,float originalAmount) {
        return DragonGustFallProtection.cap(world.getGameTime(),skyIslands$gustFallProtectionUntil,
                source.is(DamageTypes.FALL),amount);
    }
}
