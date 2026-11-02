package com.silver.skyislands.enderdragons;

import net.minecraft.world.phys.Vec3;

/** A committed moving pass. Shot placements are frozen at windup, so they can be dodged. */
public record DragonBreathRun(Vec3 direction,Vec3 aim,Vec3 spread,boolean movingTarget) {
    public static DragonBreathRun plan(Vec3 origin,Vec3 aim,Vec3 targetMovement,int pass) {
        Vec3 forward=DragonFlight.horizontal(aim.subtract(origin));
        Vec3 wing=new Vec3(-forward.z,0,forward.x).scale((pass&1)==0?1:-1);
        boolean moving=targetMovement.horizontalDistanceSqr()>.16;
        // A high dragon makes a shallow descending pass; otherwise it crosses the target's flank.
        double descent=Math.clamp((aim.y-origin.y)*.012,-.35,.15);
        Vec3 travel=forward.scale(origin.y-aim.y>14?.9:.55).add(wing.scale(.7)).add(0,descent,0).normalize();
        Vec3 spread=moving?DragonFlight.limit(targetMovement.scale(8),10):wing.scale(10);
        return new DragonBreathRun(travel,aim,spread,moving);
    }
    public Vec3 impact(Vec3 currentTarget, Vec3 movement, int shot) {
        // Each projectile gets one last lead at its launch instant. The projectile itself never homes.
        Vec3 base=currentTarget.add(DragonFlight.limit(movement.scale(6),8)).add(0,.5,0);
        int offset=movingTarget?shot:shot==0?0:shot==1?-1:1;
        return base.add(spread.scale(offset));
    }
}
