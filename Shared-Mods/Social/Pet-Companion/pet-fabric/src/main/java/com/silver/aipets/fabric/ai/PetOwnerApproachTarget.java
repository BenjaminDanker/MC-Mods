package com.silver.aipets.fabric.ai;

import net.minecraft.world.phys.Vec3;

/** A point on the owner's side of the pet that preserves conversational space. */
public final class PetOwnerApproachTarget {
    private PetOwnerApproachTarget() { }

    public static Vec3 target(Vec3 pet, Vec3 owner, double separation) {
        if (pet == null || owner == null || !Double.isFinite(separation) || separation < 3) {
            throw new IllegalArgumentException("Invalid owner approach target");
        }
        double dx = pet.x - owner.x;
        double dz = pet.z - owner.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 0.001) return pet;
        return new Vec3(owner.x + dx / horizontal * separation,
                owner.y, owner.z + dz / horizontal * separation);
    }
}
