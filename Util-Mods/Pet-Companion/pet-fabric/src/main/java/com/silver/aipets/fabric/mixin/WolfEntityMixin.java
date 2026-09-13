package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.interaction.PetInteractionRouter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Wolf.class)
public abstract class WolfEntityMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void aipets$reserveInteraction(
            Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (aipets$isMarkedPet()) {
            cir.setReturnValue(PetInteractionRouter.interact(
                    player, (Wolf) (Object) this, hand));
        }
    }

    @Inject(method = "canMate", at = @At("HEAD"), cancellable = true)
    private void aipets$preventBreeding(
            Animal other, CallbackInfoReturnable<Boolean> cir) {
        if (aipets$isMarkedPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wantsToAttack", at = @At("HEAD"), cancellable = true)
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
