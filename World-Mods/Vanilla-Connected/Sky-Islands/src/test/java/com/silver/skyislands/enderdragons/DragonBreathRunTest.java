package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DragonBreathRunTest {
    @Test void stationaryArchersGetAlternatingMovingCrossingPasses() {
        var first=DragonBreathRun.plan(new Vec3(-40,5,0),Vec3.ZERO,Vec3.ZERO,1);
        var second=DragonBreathRun.plan(new Vec3(-40,5,0),Vec3.ZERO,Vec3.ZERO,2);
        assertTrue(first.direction().x>0);
        assertEquals(-first.direction().z,second.direction().z,1e-9);
        assertEquals(new Vec3(0,.5,0),first.impact(Vec3.ZERO,Vec3.ZERO,0));
        assertEquals(0,first.impact(Vec3.ZERO,Vec3.ZERO,1).add(first.impact(Vec3.ZERO,Vec3.ZERO,2)).subtract(0,1,0).length(),1e-9);
    }
    @Test void flyersGetCappedLeadingShotsAndHighDragonDescends() {
        var pass=DragonBreathRun.plan(new Vec3(-40,30,0),Vec3.ZERO,new Vec3(3,0,0),1);
        assertTrue(pass.direction().y<0);
        assertTrue(pass.movingTarget());
        assertEquals(new Vec3(18,.5,0),pass.impact(Vec3.ZERO,new Vec3(3,0,0),1));
        assertEquals(new Vec3(28,.5,0),pass.impact(Vec3.ZERO,new Vec3(3,0,0),2));
        assertEquals(1,pass.direction().length(),1e-9);
    }
}
