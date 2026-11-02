package com.silver.skyislands.enderdragons;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
class DragonAvoidanceTest {
    @Test void repeatedSteeringPassesTheHeadWithoutEnteringItsZone() {
        Vec3 pos=new Vec3(-160,0,0), forward=new Vec3(1,0,0);
        for(int tick=0;tick<1000 && pos.x<120;tick++) {
            Vec3 dir=DragonAvoidance.steer(pos,forward,List.of(Vec3.ZERO),80,160,1);
            pos=pos.add(dir.scale(.85));
            assertTrue(pos.horizontalDistanceSqr()>=80*80-1e-6);
        }
        assertTrue(pos.x>=120,"Avoidance must resume travel rather than orbit forever");
    }
    @Test void bypassChecksEntireSegmentNotJustEndpoint() {
        Vec3 from=new Vec3(-120,0,0),head=Vec3.ZERO;
        Vec3 dir=DragonAvoidance.steer(from,new Vec3(1,0,0),List.of(head),80,160,1);
        assertTrue(DragonAvoidance.segmentDistanceSquared(from,from.add(dir.scale(160)),head)>=80*80);
        assertTrue(dir.x>0); // Pass beside it instead of needlessly reversing.
    }
    @Test void startingInsideMovesOutInsteadOfThroughHead() {
        Vec3 from=new Vec3(-10,0,0);
        Vec3 dir=DragonAvoidance.steer(from,new Vec3(1,0,0),List.of(Vec3.ZERO),80,160,1);
        assertTrue(dir.x<0);
    }
    @Test void overlappingZonesDoNotChooseNearestObstacleAsFallback() {
        Vec3 from=new Vec3(-120,0,0);
        var heads=List.of(Vec3.ZERO,new Vec3(0,0,90));
        Vec3 dir=DragonAvoidance.steer(from,new Vec3(1,0,0),heads,80,160,1);
        for(Vec3 head:heads) assertTrue(DragonAvoidance.segmentDistanceSquared(from,from.add(dir.scale(160)),head)>=80*80);
        assertTrue(dir.z<0);
    }
    @Test void noThreatPreservesHeadingAndCenterEscapeIsFinite() {
        Vec3 forward=new Vec3(1,0,0);
        assertEquals(forward,DragonAvoidance.steer(Vec3.ZERO,forward,List.of(),80,160,1));
        Vec3 escape=DragonAvoidance.steer(Vec3.ZERO,Vec3.ZERO,List.of(Vec3.ZERO),80,160,1);
        assertTrue(Double.isFinite(escape.x)); assertEquals(1,escape.length(),1e-6);
    }
}
