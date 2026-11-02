package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$preventHotbarDrop(boolean entireStack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (SpawnProtectionManager.INSTANCE.shouldBlockDrop(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$blockPvpDamage(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(source.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        ServerPlayer victim = (ServerPlayer) (Object) this;
        if (SpawnProtectionManager.INSTANCE.shouldBlockPvp(attacker, victim)) {
            cir.setReturnValue(false);
        }
    }
}
