package com.silver.skyislands.enderdragons;
import static org.junit.jupiter.api.Assertions.*;
import static com.silver.skyislands.enderdragons.DragonCombatSequence.*;
import org.junit.jupiter.api.Test;
class DragonCombatSequenceTest {
    @Test void sustainedArrowDamageCannotPreventCompletedAttacks() {
        var fight=new DragonCombatSequence();
        int interrupts=0,completed=0;
        for(int tick=0;tick<1500 && fight.stage()!=Stage.EXHAUSTED;tick++) {
            if (fight.staggerFromDamage(12)) interrupts++;
            Stage before=fight.stage();
            fight.advance(40,false,0);
            if(before==Stage.ATTACK && fight.stage()!=Stage.ATTACK) completed++;
        }
        assertEquals(1,interrupts);
        assertEquals(3,completed);
        assertEquals(Stage.EXHAUSTED,fight.stage());
    }
    @Test void proximityDamageIsEnabledOnlyDuringCommittedSwoops() {
        var fight=new DragonCombatSequence();
        boolean sawSwoop=false,sawBreath=false;
        for(int tick=0;tick<1000;tick++) {
            assertEquals(fight.stage()==Stage.ATTACK && fight.attack()==Attack.SWOOP,fight.contactAttack());
            if(fight.contactAttack()) sawSwoop=true;
            if(fight.stage()==Stage.ATTACK && fight.attack()==Attack.BREATH_SWEEP) sawBreath=true;
            fight.advance(40,false,0);
        }
        assertTrue(sawSwoop && sawBreath);
        fight.stagger();
        assertFalse(fight.contactAttack());
    }
    @Test void tacticalChoiceCannotRetargetAnAttackDuringItsWindup() {
        var fight=new DragonCombatSequence();
        for(int i=0;i<25;i++) fight.advance(40,false,0,Attack.SWOOP);
        assertEquals(Attack.SWOOP,fight.attack());
        for(int i=0;i<40;i++) fight.advance(10,false,0,Attack.WING_GUST);
        assertEquals(Stage.ATTACK,fight.stage());
        assertEquals(Attack.SWOOP,fight.attack());
    }
    @Test void tacticalPreferenceCannotSpamOneAttackOrCreateDistantGusts() {
        var fight=new DragonCombatSequence();
        for(int i=0;i<25;i++) fight.advance(40,false,0,Attack.WING_GUST);
        assertNotEquals(Attack.WING_GUST,fight.attack());
        Attack previous=fight.attack();
        fight.stagger();
        for(int i=0;i<100;i++) fight.advance(40,false,0,previous);
        assertNotEquals(previous,fight.attack());
    }
    @Test void engagementStartsApproachInsteadOfStaggerRecovery() {
        var fight=new DragonCombatSequence();
        fight.stagger();
        fight.engage();
        assertEquals(Stage.APPROACH,fight.stage());
        assertEquals(0,fight.age());
        for(int i=0;i<25;i++) fight.advance(40,false,0);
        assertEquals(Stage.WINDUP,fight.stage());
    }
    @Test void threeCompletedAttacksCreateFourSecondHeadWeakness() {
        var fight=new DragonCombatSequence();
        int completed=0;
        for(int i=0;i<1500 && fight.stage()!=Stage.EXHAUSTED;i++) {
            Stage before=fight.stage();
            assertEquals(10f,fight.headDamage(10));
            fight.advance(40,false,0);
            if(before==Stage.ATTACK && fight.stage()!=Stage.ATTACK) completed++;
        }
        assertEquals(3,completed);
        assertEquals(Stage.EXHAUSTED,fight.stage());
        assertEquals(15f,fight.headDamage(10));
        for(int i=0;i<79;i++) fight.advance(40,true,0);
        assertEquals(Stage.EXHAUSTED,fight.stage());
        fight.advance(40,true,0);
        assertEquals(Stage.APPROACH,fight.stage());
        assertEquals(10f,fight.headDamage(10));
    }
    @Test void interruptedWindupsDoNotCountAsCompletedAttacks() {
        var fight=new DragonCombatSequence();
        for(int cycle=0;cycle<6;cycle++) {
            while(fight.stage()!=Stage.WINDUP) fight.advance(40,false,0);
            fight.stagger();
            assertEquals(Stage.RECOVER,fight.stage());
            assertEquals(10f,fight.headDamage(10));
        }
        fight.engage();
        assertEquals(Stage.APPROACH,fight.stage());
        assertEquals(10f,fight.headDamage(10));
    }
    @Test void attacksRequireWindupAndAlwaysHaveRecovery() {
        var fight=new DragonCombatSequence();
        for(int i=0;i<12;i++) fight.advance(40,false,0);
        assertEquals(Stage.WINDUP,fight.stage());
        for(int i=0;i<19;i++) fight.advance(40,false,0);
        assertEquals(Stage.WINDUP,fight.stage());
        fight.advance(40,false,0);assertEquals(Stage.ATTACK,fight.stage());
        for(int i=0;i<45;i++) fight.advance(40,false,0);
        assertEquals(Stage.RECOVER,fight.stage());
        for(int i=0;i<31;i++) fight.advance(40,false,0);
        assertEquals(Stage.RECOVER,fight.stage());
        fight.advance(40,false,0);assertEquals(Stage.APPROACH,fight.stage());
    }
    @Test void staggerCancelsAttackAndCreatesLongOpening() {
        var fight=new DragonCombatSequence();fight.stagger();
        for(int i=0;i<74;i++) fight.advance(10,true,0);
        assertEquals(Stage.RECOVER,fight.stage());
        fight.advance(10,true,0);assertEquals(Stage.APPROACH,fight.stage());
    }
    @Test void gustRequiresCloseTargetAndAttacksDoNotRepeat() {
        var fight=new DragonCombatSequence();
        for(int i=0;i<25;i++) fight.advance(10,false,0);
        assertEquals(Attack.WING_GUST,fight.attack());
        fight.stagger();for(int i=0;i<100;i++) fight.advance(10,false,0);
        assertEquals(Attack.SWOOP,fight.attack());
    }
    @Test void distantTargetsDoNotTriggerBlindAttacks() {
        var fight=new DragonCombatSequence();for(int i=0;i<1000;i++) fight.advance(100,false,0);
        assertEquals(Stage.APPROACH,fight.stage());
    }
}
