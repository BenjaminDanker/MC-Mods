package com.silver.skyislands.enderdragons;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Event-driven defensive response to concentrated melee pressure. */
public final class DragonSphericalGust {
    public static final int HIT_THRESHOLD=4;
    public static final long HIT_WINDOW_TICKS=60;
    public static final long COOLDOWN_TICKS=160;
    public static final double RADIUS=30;
    public static final double DEFAULT_IMPULSE_STRENGTH=1.7;
    public static final double DEFAULT_MINIMUM_OUTWARD_SPEED=.20;
    public static final double DEFAULT_MAXIMUM_RADIAL_SPEED=.35;

    private record Hit(UUID player,long tick) {}
    private final ArrayDeque<Hit> hits=new ArrayDeque<>();
    private final HashMap<UUID,Long> lastAcceptedTick=new HashMap<>();
    private long cooldownUntil=Long.MIN_VALUE;

    public boolean recordMeleeHit(UUID player,long now) {
        trim(now);
        if (now<cooldownUntil || lastAcceptedTick.getOrDefault(player,Long.MIN_VALUE)==now) return false;
        lastAcceptedTick.put(player,now);
        hits.addLast(new Hit(player,now));
        if (hits.size()<HIT_THRESHOLD) return false;
        hits.clear();
        cooldownUntil=now+COOLDOWN_TICKS;
        return true;
    }

    private void trim(long now) {
        while(!hits.isEmpty() && now-hits.getFirst().tick()>HIT_WINDOW_TICKS) hits.removeFirst();
        lastAcceptedTick.entrySet().removeIf(e -> now-e.getValue()>HIT_WINDOW_TICKS);
    }

    /**
     * Adds outward radial speed while preserving all tangential velocity exactly.
     * The outward floor prevents an incoming player's momentum from cancelling the burst.
     */
    public static Vec3 velocity(Vec3 dragonPosition,Vec3 playerPosition,Vec3 currentVelocity,
                                double impulseStrength,double minimumOutwardSpeed,double maximumRadialSpeed) {
        Vec3 offset=playerPosition.subtract(dragonPosition);
        if (offset.lengthSqr()<1e-9) return currentVelocity;
        Vec3 directionAway=offset.normalize();
        double radialSpeed=currentVelocity.dot(directionAway);
        Vec3 radialVelocity=directionAway.scale(radialSpeed);
        Vec3 tangentialVelocity=currentVelocity.subtract(radialVelocity);
        double newRadialSpeed=Math.clamp(Math.max(radialSpeed+impulseStrength,minimumOutwardSpeed),
                0,maximumRadialSpeed);
        return tangentialVelocity.add(directionAway.scale(newRadialSpeed));
    }
}
