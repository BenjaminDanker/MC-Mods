package com.silver.skyislands.enderdragons;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Checks the swept path, not merely a safe-looking destination. No world reads or chunk tickets. */
public final class DragonAvoidance {
    private DragonAvoidance() {}
    public static double segmentDistanceSquared(Vec3 start, Vec3 end, Vec3 point) {
        double dx=end.x-start.x, dz=end.z-start.z;
        double length=dx*dx+dz*dz;
        double t=length < 1e-9 ? 0 : Math.clamp(((point.x-start.x)*dx+(point.z-start.z)*dz)/length,0,1);
        double x=start.x+t*dx-point.x, z=start.z+t*dz-point.z;
        return x*x+z*z;
    }
    public static Vec3 steer(Vec3 position, Vec3 desired, List<Vec3> heads, double radius, double ahead, int side) {
        Vec3 forward=new Vec3(desired.x,0,desired.z);
        forward=forward.lengthSqr()<1e-9 ? new Vec3(1,0,0) : forward.normalize();
        double threshold=radius*radius;
        if (clearance(position,position.add(forward.scale(ahead)),heads)>=threshold) return forward;
        boolean inside=clearance(position,position,heads)<threshold;
        Vec3 best=forward;
        double bestScore=-Double.MAX_VALUE;
        for (int i=0;i<32;i++) {
            double angle=i*Math.PI/16;
            Vec3 candidate=new Vec3(forward.x*Math.cos(angle)-forward.z*Math.sin(angle),0,
                    forward.x*Math.sin(angle)+forward.z*Math.cos(angle));
            Vec3 end=position.add(candidate.scale(ahead));
            double swept=clearance(position,end,heads);
            double endpoint=clearance(end,end,heads);
            // Safe paths dominate. Inside a zone, choose the strongest escape, never the nearest obstruction.
            double score=inside ? swept*10+endpoint
                    : (swept>=threshold ? 1e12 : swept);
            score+=candidate.dot(forward)*10 + side*Math.sin(angle)*.1;
            if (score>bestScore) { bestScore=score;best=candidate; }
        }
        return best;
    }
    private static double clearance(Vec3 start, Vec3 end,List<Vec3> heads) {
        double closest=Double.MAX_VALUE;
        for (Vec3 head:heads) closest=Math.min(closest,segmentDistanceSquared(start,end,head));
        return closest;
    }
}
