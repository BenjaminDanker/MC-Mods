package com.silver.aipets.fabric.interaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;

@FunctionalInterface
public interface PetInteractionHandler {
    /** Opens or reports the deterministic interaction state; it must not invoke AI on open. */
    void open(ServerPlayer owner, TamableAnimal pet);
}
