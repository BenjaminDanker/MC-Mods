package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$blockPvpDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }

        ServerPlayerEntity victim = (ServerPlayerEntity) (Object) this;
        if (SpawnProtectionManager.INSTANCE.shouldBlockPvp(attacker, victim)) {
            cir.setReturnValue(false);
        }
    }
}
