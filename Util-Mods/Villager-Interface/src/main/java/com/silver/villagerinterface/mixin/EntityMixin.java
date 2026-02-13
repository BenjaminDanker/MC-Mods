package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.villager.CustomVillagerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "canStartRiding(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void villagerinterface$preventCustomVillagerRiding(Entity vehicle, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof VillagerEntity villager)) {
            return;
        }

        if (!(villager instanceof CustomVillagerData data) || data.villagerinterface$getCustomId() == null) {
            return;
        }

        cir.setReturnValue(false);
        cir.cancel();
    }
}
