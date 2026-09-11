package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.interaction.PetInteractionRouter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WolfEntity.class)
public abstract class WolfEntityMixin {
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void aipets$reserveInteraction(
            PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (aipets$isMarkedPet()) {
            cir.setReturnValue(PetInteractionRouter.interact(
                    player, (WolfEntity) (Object) this, hand));
        }
    }

    @Inject(method = "canBreedWith", at = @At("HEAD"), cancellable = true)
    private void aipets$preventBreeding(
            AnimalEntity other, CallbackInfoReturnable<Boolean> cir) {
        if (aipets$isMarkedPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canAttackWithOwner", at = @At("HEAD"), cancellable = true)
    private void aipets$preventOwnerCombatAssistance(
            LivingEntity target,
            LivingEntity owner,
            CallbackInfoReturnable<Boolean> cir) {
        if (aipets$isMarkedPet()) {
            cir.setReturnValue(false);
        }
    }

    private boolean aipets$isMarkedPet() {
        return (Object) this instanceof PetEntityData data && data.aipets$isPet();
    }
}
