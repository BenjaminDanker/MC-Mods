package com.silver.aipets.fabric.interaction;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface PetInteractionHandler {
    /** Opens or reports the deterministic interaction state; it must not invoke AI on open. */
    void open(ServerPlayerEntity owner, TameableEntity pet);
}
