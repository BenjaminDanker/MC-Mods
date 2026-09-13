package com.silver.skyislands.portal.mixins;

import com.silver.skyislands.portal.VoidDeathRedirectHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityVoidRedirectMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void skyislands$redirectBeforeLethalVoidDamage(
        ServerLevel world,
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (VoidDeathRedirectHandler.tryRedirectBeforeLethalVoidDamage(player, world, source, amount)) {
            cir.setReturnValue(false);
        }
    }
}
