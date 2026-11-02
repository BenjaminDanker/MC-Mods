package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DragonFlightTest {
    @Test void rangedTargetsCausePursuitInsteadOfAnEndlessBank() {
        var flight=new DragonFlight();
        var threats=List.of(threat(45,0,10,false,true));
        flight.approach(Vec3.ZERO,FORWARD,threats,0);
        assertEquals(DragonFlight.Maneuver.PURSUE,flight.maneuver());
        flight.approach(Vec3.ZERO,FORWARD,threats,30);
        assertEquals(DragonFlight.Maneuver.PURSUE,flight.maneuver());
    }
    @Test void soloAndGroupFlightScenariosProduceClosingPasses() {
        for(int count:new int[]{1,4,20}) {
            var flight=new DragonFlight();
            Vec3 position=new Vec3(-60,35,0), velocity=FORWARD;
            boolean linedUp=false;
            double travel=0;
            for(int tick=0;tick<400;tick++) {
                var threats=new java.util.ArrayList<DragonFlight.Threat>();
                threats.add(threat(0,0,0,false,true));
                for(int i=1;i<count;i++) {
                    double angle=i*Math.PI*2/count;
                    threats.add(threat(Math.cos(angle)*24, i%2==0?15:0,Math.sin(angle)*24,i%2==0,i%3==0));
                }
                Vec3 direction=flight.approach(position,velocity,threats,tick);
                velocity=DragonFlight.steer(velocity,direction,1,flight.bank());
                position=position.add(velocity);
                travel+=velocity.length();
                assertTrue(Double.isFinite(position.lengthSqr()));
                if(position.length()<64 && DragonFlight.attackReady(position,velocity,Vec3.ZERO)) linedUp=true;
            }
            assertTrue(linedUp,"No closing pass for "+count+" players");
            assertTrue(travel>300,"Flight stalled for "+count+" players");
        }
    }
    private static final Vec3 FORWARD=new Vec3(1,0,0);
    private static DragonFlight.Threat threat(double x,double y,double z,boolean flying,boolean ranged) {
        return new DragonFlight.Threat(new Vec3(x,y,z),Vec3.ZERO,flying,ranged);
    }
    @Test void surroundedDragonBreaksOutInsteadOfOrbiting() {
        var flight=new DragonFlight();
        Vec3 direction=flight.approach(Vec3.ZERO,FORWARD,List.of(threat(10,0,10,true,false),
                threat(-10,0,10,true,false),threat(-10,0,-10,true,false),threat(10,0,-10,true,false)),0);
        assertEquals(DragonFlight.Maneuver.BREAK_OUT,flight.maneuver());
        assertTrue(direction.y>0);
        assertTrue(direction.horizontalDistance()>40);
    }
    @Test void pressureBelowCausesClimbButDistantFlyerCausesPursuit() {
        var flight=new DragonFlight();
        flight.approach(Vec3.ZERO,FORWARD,List.of(threat(8,-10,0,true,false)),0);
        assertEquals(DragonFlight.Maneuver.CLIMB,flight.maneuver());
        flight.approach(Vec3.ZERO,FORWARD,List.of(threat(80,10,0,true,false)),30);
        assertEquals(DragonFlight.Maneuver.PURSUE,flight.maneuver());
    }
    @Test void baitingWorksUntilCommittedDecisionExpires() {
        var flight=new DragonFlight();
        Vec3 first=flight.approach(Vec3.ZERO,FORWARD,List.of(threat(80,0,0,true,false)),0);
        Vec3 held=flight.approach(Vec3.ZERO,FORWARD,List.of(threat(-80,0,0,true,false)),29);
        assertEquals(first,held);
        assertTrue(flight.approach(Vec3.ZERO,FORWARD,List.of(threat(-80,0,0,true,false)),30).x<0);
    }
    @Test void rangedPressureChangesAttackChoiceWithoutAvoidingTheArcher() {
        var flight=new DragonFlight();
        var threats=List.of(threat(45,0,10,false,true));
        flight.approach(Vec3.ZERO,FORWARD,threats,0);
        assertEquals(DragonFlight.Maneuver.PURSUE,flight.maneuver());
        assertEquals(-1,flight.bank());
        assertEquals(DragonCombatSequence.Attack.BREATH_SWEEP,DragonFlight.preferredAttack(Vec3.ZERO,FORWARD,threats));
        assertEquals(DragonCombatSequence.Attack.WING_GUST,DragonFlight.preferredAttack(Vec3.ZERO,FORWARD,
                List.of(threat(8,0,2,true,false),threat(8,0,-2,true,false))));
        assertEquals(DragonCombatSequence.Attack.WING_GUST,DragonFlight.preferredAttack(Vec3.ZERO,FORWARD,
                List.of(threat(20,0,0,false,true))));
    }
    @Test void aboutFaceBanksWithoutStallingOrExceedingTurnLimit() {
        Vec3 velocity=FORWARD;
        for(int i=0;i<40;i++) {
            Vec3 next=DragonFlight.steer(velocity,FORWARD.scale(-1),1,1);
            assertTrue(next.length()>.95);
            assertTrue(next.length()<=1.000001);
            assertTrue(Math.acos(Math.clamp(next.normalize().dot(velocity.normalize()),-1,1))<=.130001);
            velocity=next;
        }
        assertTrue(velocity.x<-.95);
    }
}
