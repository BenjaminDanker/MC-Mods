package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DragonDraftTest {
    @Test void attackDraftsAccelerateOnlyVertically() {
        Vec3 up=DragonDraft.updraft(Vec3.ZERO).sample(Vec3.ZERO);
        Vec3 down=DragonDraft.downdraft(Vec3.ZERO).sample(Vec3.ZERO);
        assertEquals(0,up.x); assertEquals(0,up.z); assertEquals(.24,up.y,1e-9);
        assertEquals(0,down.x); assertEquals(0,down.z); assertEquals(-.24,down.y,1e-9);
    }
    @Test void columnsHaveClearBoundsAndSmoothFalloff() {
        var draft=DragonDraft.updraft(Vec3.ZERO);
        assertTrue(draft.sample(new Vec3(7,0,0)).y>0);
        assertTrue(draft.sample(new Vec3(0,12,0)).y>0);
        assertEquals(Vec3.ZERO,draft.sample(new Vec3(14,0,0)));
        assertEquals(Vec3.ZERO,draft.sample(new Vec3(0,24,0)));
    }
    @Test void verticalSpeedCapsDoNotReverseOrStopExistingMotion() {
        assertEquals(.24,DragonDraft.verticalImpulse(0,.24),1e-9);
        assertEquals(-.24,DragonDraft.verticalImpulse(0,-.24),1e-9);
        assertEquals(0,DragonDraft.verticalImpulse(2,.24),1e-9);
        assertEquals(0,DragonDraft.verticalImpulse(-1.2,-.24),1e-9);
        assertEquals(.1,DragonDraft.verticalImpulse(1.7,.24),1e-9);
        assertEquals(-.1,DragonDraft.verticalImpulse(-.9,-.24),1e-9);
    }
    @Test void velocityPacketPreservesForwardAndSidewaysFlightExactly() {
        Vec3 flight=new Vec3(2.35,-.3,-1.72);
        Vec3 lifted=DragonDraft.applyVertical(flight,.24);
        Vec3 pushedDown=DragonDraft.applyVertical(flight,-.24);
        assertEquals(flight.x,lifted.x); assertEquals(flight.z,lifted.z);
        assertEquals(flight.x,pushedDown.x); assertEquals(flight.z,pushedDown.z);
        assertEquals(-.06,lifted.y,1e-9);
        assertEquals(-.54,pushedDown.y,1e-9);
    }
}
