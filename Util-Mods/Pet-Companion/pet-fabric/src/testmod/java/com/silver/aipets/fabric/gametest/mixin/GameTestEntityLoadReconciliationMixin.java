package com.silver.aipets.fabric.gametest.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.gametest.GameTestReconciliationGate;
import com.silver.aipets.fabric.reconciliation.PeriodicPetReconciliation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Testmod-only isolation for entity-load fixtures sharing one GameTest world. */
@Mixin(PeriodicPetReconciliation.class)
public abstract class GameTestEntityLoadReconciliationMixin {
    @Inject(method = "onEntityLoad", at = @At("HEAD"), cancellable = true)
    private void aipets$onlyReconcileExplicitFixture(Entity entity, ServerLevel world, CallbackInfo ci) {
        if (!(entity instanceof PetEntityData data) || !data.aipets$isPet()
                || !GameTestReconciliationGate.enabled(data.aipets$getPetId())) {
            ci.cancel();
        }
    }

    @Inject(method = "onEndWorldTick", at = @At("HEAD"), cancellable = true)
    private void aipets$onlyRunExplicitPeriodicFixture(ServerLevel world, CallbackInfo ci) {
        if (!GameTestReconciliationGate.periodicEnabled()) ci.cancel();
    }

    @Inject(method = "reconcile", at = @At("HEAD"), cancellable = true)
    private void aipets$neverReconcileAnotherTestFixture(Entity entity, ServerLevel world, CallbackInfo ci) {
        if (!(entity instanceof PetEntityData data) || !data.aipets$isPet()
                || !GameTestReconciliationGate.enabled(data.aipets$getPetId())) {
            ci.cancel();
        }
    }
}
