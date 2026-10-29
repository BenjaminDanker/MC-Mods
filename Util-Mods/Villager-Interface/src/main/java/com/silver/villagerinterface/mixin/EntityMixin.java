package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.villager.CustomVillagerData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void villagerinterface$preventCustomVillagerRiding(Entity vehicle, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Villager villager)) {
            return;
        }

        if (!(villager instanceof CustomVillagerData data) || data.villagerinterface$getCustomId() == null) {
            return;
        }

        cir.setReturnValue(false);
        cir.cancel();
    }

    /** A cutscene may hide an entity from the one player watching it (they see a scripted copy instead). */
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void villagerinterface$hiddenInScene(net.minecraft.server.level.ServerPlayer player,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (com.silver.villagerinterface.cutscene.Actors.hiddenFrom(player, (Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
