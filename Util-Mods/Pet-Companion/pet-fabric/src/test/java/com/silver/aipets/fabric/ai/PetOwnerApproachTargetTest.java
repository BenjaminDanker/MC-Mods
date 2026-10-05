package com.silver.aipets.fabric.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PetOwnerApproachTargetTest {
    @Test void targetsStayOutsideThreeBlocksIncludingDiagonals() {
        Vec3 owner = new Vec3(8, 64, -4);
        Vec3 target = PetOwnerApproachTarget.target(new Vec3(20, 64, 12), owner, 3.5);
        assertEquals(3.5, Math.hypot(target.x - owner.x, target.z - owner.z), 0.0001);
        assertEquals(owner.y, target.y);
    }
}
