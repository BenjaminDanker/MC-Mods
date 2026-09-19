package com.silver.skyislands.enderdragons;

import net.minecraft.world.phys.Vec3;

/** A stationary vertical current created by a deliberate physical attack. */
public record DragonDraft(Vec3 center, Kind kind, double strength) {
    public enum Kind { UPDRAFT, DOWNDRAFT }
    public static DragonDraft updraft(Vec3 center) { return new DragonDraft(center,Kind.UPDRAFT,1); }
    public static DragonDraft downdraft(Vec3 center) { return new DragonDraft(center,Kind.DOWNDRAFT,1); }

    private static double falloff(Vec3 point,Vec3 origin,double radius,double halfHeight) {
        Vec3 d=point.subtract(origin);
        double q=(d.x*d.x+d.z*d.z)/(radius*radius)+d.y*d.y/(halfHeight*halfHeight);
        return Math.max(0,1-q)*Math.max(0,1-q);
    }

    /** Pure vertical acceleration. Drafts never replace or oppose horizontal player movement. */
    public Vec3 sample(Vec3 point) {
        double amount=.24*strength*falloff(point,center,14,24);
        return new Vec3(0,kind==Kind.UPDRAFT?amount:-amount,0);
    }

    public static double verticalImpulse(double velocity,double impulse) {
        double cap=impulse>0?1.8:-1.0;
        return impulse>0 ? Math.min(impulse,Math.max(0,cap-velocity))
                : Math.max(impulse,Math.min(0,cap-velocity));
    }

    /** Build the velocity packet from accepted client movement, changing Y and preserving X/Z exactly. */
    public static Vec3 applyVertical(Vec3 clientMovement,double impulse) {
        return clientMovement.add(0,verticalImpulse(clientMovement.y,impulse),0);
    }
}
