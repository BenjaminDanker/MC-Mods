package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class AnimalEntityMixin {
    @Inject(method = "setInLove", at = @At("HEAD"), cancellable = true)
    private void aipets$preventLoveMode(Player player, CallbackInfo ci) {
        if (aipets$isMarkedPet()) {
            ci.cancel();
        }
    }

    @Inject(method = "setInLoveTime", at = @At("HEAD"), cancellable = true)
    private void aipets$preventLoveTicks(int loveTicks, CallbackInfo ci) {
        if (aipets$isMarkedPet() && loveTicks != 0) {
            ci.cancel();
        }
    }

    @Inject(
            method = "spawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventBreeding(
            ServerLevel world, Animal mate, CallbackInfo ci) {
        if (aipets$isMarkedPet()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "finalizeSpawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/AgeableMob;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventBreedingWithChild(
            ServerLevel world, Animal mate, AgeableMob child, CallbackInfo ci) {
        if (aipets$isMarkedPet()) {
            ci.cancel();
        }
    }

    private boolean aipets$isMarkedPet() {
        return (Object) this instanceof PetEntityData data && data.aipets$isPet();
    }
}
