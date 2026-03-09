package com.silver.skyislands.portal.mixins;

import com.silver.skyislands.portal.VoidDeathRedirectHandler;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityVoidRedirectMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void skyislands$redirectBeforeLethalVoidDamage(
        ServerWorld world,
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (VoidDeathRedirectHandler.tryRedirectBeforeLethalVoidDamage(player, world, source, amount)) {
            cir.setReturnValue(false);
        }
    }
}