package com.silver.atlantis.spawn.mixin;

import com.silver.atlantis.spawn.SpecialDropManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "dropLoot", at = @At("TAIL"))
    private void atlantis$dropSpecial(ServerWorld world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        SpecialDropManager.tryDropIfSpecial((LivingEntity) (Object) this);
    }
}
