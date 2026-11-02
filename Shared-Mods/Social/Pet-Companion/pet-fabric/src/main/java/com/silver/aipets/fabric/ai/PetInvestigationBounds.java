package com.silver.aipets.fabric.ai;

import net.minecraft.core.BlockPos;

/** Conservative owner-centered envelope for short investigations; never requests a ticket. */
public final class PetInvestigationBounds {
    private PetInvestigationBounds() { }

    public static boolean contains(BlockPos owner, BlockPos pet, BlockPos target,
            int simulationDistance) {
        if (simulationDistance < 2) return false;
        int chunkMargin = Math.max(0, simulationDistance - 3);
        if (Math.abs((target.getX() >> 4) - (owner.getX() >> 4)) > chunkMargin
                || Math.abs((target.getZ() >> 4) - (owner.getZ() >> 4)) > chunkMargin)
            return false;
        long ownerDx = (long) target.getX() - owner.getX();
        long ownerDz = (long) target.getZ() - owner.getZ();
        long petDx = (long) target.getX() - pet.getX();
        long petDz = (long) target.getZ() - pet.getZ();
        return ownerDx * ownerDx + ownerDz * ownerDz <= 64
                && petDx * petDx + petDz * petDz <= 32;
    }
}
