package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {
    @Inject(method = "lovePlayer", at = @At("HEAD"), cancellable = true)
    private void aipets$preventLoveMode(PlayerEntity player, CallbackInfo ci) {
        if (aipets$isMarkedPet()) {
            ci.cancel();
        }
    }

    @Inject(method = "setLoveTicks", at = @At("HEAD"), cancellable = true)
    private void aipets$preventLoveTicks(int loveTicks, CallbackInfo ci) {
        if (aipets$isMarkedPet() && loveTicks != 0) {
            ci.cancel();
        }
    }

    @Inject(
            method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventBreeding(
            ServerWorld world, AnimalEntity mate, CallbackInfo ci) {
        if (aipets$isMarkedPet()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/PassiveEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventBreedingWithChild(
            ServerWorld world, AnimalEntity mate, PassiveEntity child, CallbackInfo ci) {
        if (aipets$isMarkedPet()) {
            ci.cancel();
        }
    }

    private boolean aipets$isMarkedPet() {
        return (Object) this instanceof PetEntityData data && data.aipets$isPet();
    }
}
