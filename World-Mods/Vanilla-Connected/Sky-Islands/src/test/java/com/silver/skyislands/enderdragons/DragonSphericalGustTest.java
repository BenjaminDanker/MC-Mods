package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DragonSphericalGustTest {
    private static Vec3 gust(Vec3 playerPosition,Vec3 velocity) {
        return DragonSphericalGust.velocity(Vec3.ZERO,playerPosition,velocity,
                DragonSphericalGust.DEFAULT_IMPULSE_STRENGTH,
                DragonSphericalGust.DEFAULT_MINIMUM_OUTWARD_SPEED,
                DragonSphericalGust.DEFAULT_MAXIMUM_RADIAL_SPEED);
    }

    @Test void preservesTangentialMotionAndAddsOnlyRadialSpeed() {
        Vec3 result=gust(new Vec3(10,0,0),new Vec3(0,1.25,-2));
        assertEquals(DragonSphericalGust.DEFAULT_MAXIMUM_RADIAL_SPEED,result.x,1e-9);
        assertEquals(1.25,result.y,1e-9);
        assertEquals(-2,result.z,1e-9);
    }
    @Test void fullThreeDimensionalDirectionPushesAboveAndBelowCorrectly() {
        Vec3 above=gust(new Vec3(0,10,0),Vec3.ZERO);
        Vec3 below=gust(new Vec3(0,-10,0),Vec3.ZERO);
        assertEquals(DragonSphericalGust.DEFAULT_MAXIMUM_RADIAL_SPEED,above.y,1e-9);
        assertEquals(-DragonSphericalGust.DEFAULT_MAXIMUM_RADIAL_SPEED,below.y,1e-9);
        assertEquals(0,above.x); assertEquals(0,above.z);
    }
    @Test void inwardMotionGetsMinimumOutwardLaunchAndOutwardMotionIsCapped() {
        assertEquals(DragonSphericalGust.DEFAULT_MINIMUM_OUTWARD_SPEED,
                gust(new Vec3(1,0,0),new Vec3(-1.5,0,0)).x,1e-9);
        assertEquals(DragonSphericalGust.DEFAULT_MAXIMUM_RADIAL_SPEED,
                gust(new Vec3(1,0,0),new Vec3(10,0,0)).x,1e-9);
    }
    @Test void defaultMinimumTargetsTwentyBlocksUnderElytraHorizontalDamping() {
        double estimatedDisplacement=DragonSphericalGust.DEFAULT_MINIMUM_OUTWARD_SPEED/(1-.99);
        assertEquals(20,estimatedDisplacement,1e-9);
    }
    @Test void configuredValuesControlImpulseFloorAndCap() {
        Vec3 inward=DragonSphericalGust.velocity(Vec3.ZERO,new Vec3(1,0,0),new Vec3(-2,3,0),
                1.5,.4,.8);
        Vec3 outward=DragonSphericalGust.velocity(Vec3.ZERO,new Vec3(1,0,0),new Vec3(.6,3,0),
                1.5,.4,.8);
        assertEquals(.4,inward.x,1e-9);
        assertEquals(.8,outward.x,1e-9);
        assertEquals(3,inward.y,1e-9);
        assertEquals(3,outward.y,1e-9);
    }
    @Test void fourRollingMeleeHitsTriggerThenCooldown() {
        var gust=new DragonSphericalGust();
        UUID first=new UUID(0,1),second=new UUID(0,2);
        assertFalse(gust.recordMeleeHit(first,0));
        assertFalse(gust.recordMeleeHit(second,10));
        assertFalse(gust.recordMeleeHit(first,20));
        assertTrue(gust.recordMeleeHit(second,30));
        for(int tick=31;tick<190;tick+=10) assertFalse(gust.recordMeleeHit(first,tick));
        assertFalse(gust.recordMeleeHit(first,190));
        assertFalse(gust.recordMeleeHit(second,191));
        assertFalse(gust.recordMeleeHit(first,192));
        assertTrue(gust.recordMeleeHit(second,193));
    }
    @Test void duplicateDamageHooksAndIsolatedHitsDoNotTrigger() {
        var gust=new DragonSphericalGust();
        UUID player=new UUID(0,1);
        assertFalse(gust.recordMeleeHit(player,0));
        assertFalse(gust.recordMeleeHit(player,0));
        assertFalse(gust.recordMeleeHit(player,100));
        assertFalse(gust.recordMeleeHit(player,200));
        assertFalse(gust.recordMeleeHit(player,300));
    }
}
