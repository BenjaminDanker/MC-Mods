package com.silver.skyislands.enderdragons;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Bounded local steering, not a world pathfinder. Decisions stay committed between observations. */
public final class DragonFlight {
    public record Threat(Vec3 position, Vec3 velocity, boolean flying, boolean ranged) {}
    public enum Maneuver { PURSUE, BANK, CLIMB, BREAK_OUT }
    private Maneuver maneuver = Maneuver.PURSUE;
    private Vec3 goal = Vec3.ZERO;
    private long reconsider;
    private long nextEvasion;
    private int bank = 1;

    public Maneuver maneuver() { return maneuver; }
    public int bank() { return bank; }
    public void reset() { reconsider = Long.MIN_VALUE; nextEvasion=Long.MIN_VALUE; }
    public static boolean attackReady(Vec3 position,Vec3 velocity,Vec3 target) {
        Vec3 delta=target.subtract(position);
        return horizontal(velocity).dot(horizontal(delta))>.65
                && Math.abs(delta.y)<Math.max(12,delta.horizontalDistance()*.7);
    }

    public static DragonCombatSequence.Attack preferredAttack(Vec3 position, Vec3 velocity, List<Threat> threats) {
        if (threats.isEmpty()) return null;
        int inFront=0;
        Vec3 forward=horizontal(velocity);
        for (Threat threat:threats) {
            Vec3 delta=threat.position.subtract(position);
            if (delta.lengthSqr()<18*18 && horizontal(delta).dot(forward)>.35) inFront++;
        }
        if (inFront>=2 || threats.getFirst().position.distanceTo(position)<28)
            return DragonCombatSequence.Attack.WING_GUST;
        Threat target=threats.getFirst();
        return target.ranged || !target.flying ? DragonCombatSequence.Attack.BREATH_SWEEP : DragonCombatSequence.Attack.SWOOP;
    }

    public Vec3 approach(Vec3 position, Vec3 velocity, List<Threat> threats, long tick) {
        if (threats.isEmpty()) return velocity;
        if (tick >= reconsider) {
            reconsider = tick + 30; // An opening lasts 1.5 seconds; no instantaneous counter-steering.
            Threat target = threats.getFirst();
            Vec3 forward = horizontal(velocity);
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            Vec3 pressure = Vec3.ZERO;
            int sectors = 0, below = 0, close = 0;
            double leftWeight = 0, rightWeight = 0;
            for (Threat threat : threats) {
                Vec3 delta = threat.position.subtract(position);
                double distance = delta.length();
                if (distance > 80) continue;
                double weight = 1 / Math.max(8, distance);
                pressure = pressure.add(horizontal(delta).scale(weight));
                if (delta.dot(right) > 0) rightWeight += weight; else leftWeight += weight;
                if (distance < 36) {
                    close++;
                    if (delta.y < -5) below++;
                    int sector = Math.floorMod((int)Math.floor(Math.atan2(delta.z, delta.x) / (Math.PI / 2)), 4);
                    sectors |= 1 << sector;
                }
            }
            // Turn through the less occupied wing; retain the last side in a tie.
            if (Math.abs(leftWeight - rightWeight) > .01) bank = rightWeight < leftWeight ? 1 : -1;
            Vec3 liftLane = right.scale(bank * 18);
            double distance = position.distanceTo(target.position);
            boolean evade=tick>=nextEvasion;
            if (evade && Integer.bitCount(sectors) >= 3) {
                maneuver = Maneuver.BREAK_OUT;
                Vec3 escape = pressure.lengthSqr() < 1e-6 ? right.scale(bank) : pressure.scale(-1);
                goal = position.add(horizontal(escape).scale(52)).add(0, 14, 0);
            } else if (evade && (below >= 2 || (below > 0 && close == 1 && distance < 20))) {
                maneuver = Maneuver.CLIMB;
                goal = position.add(forward.scale(26)).add(liftLane).add(0, 18, 0);
            } else if (evade && distance < 30) {
                maneuver = Maneuver.BANK;
                goal = target.position.add(liftLane.scale(1.8)).add(forward.scale(18)).add(0, target.flying ? 4 : 10, 0);
            } else {
                maneuver = Maneuver.PURSUE;
                // Lead a flyer, but cap it so sudden reversals and baiting still work.
                Vec3 lead = limit(target.velocity.scale(target.flying ? 14 : 6), 18);
                goal = target.position.add(lead).add(0, target.flying ? 3 : 9, 0);
            }
            if (maneuver!=Maneuver.PURSUE) nextEvasion=tick+120;
        }
        return goal.subtract(position);
    }

    public static Vec3 horizontal(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0, v.z);
        return flat.lengthSqr() < 1e-9 ? new Vec3(1, 0, 0) : flat.normalize();
    }
    public static Vec3 limit(Vec3 v, double max) { return v.lengthSqr() > max * max ? v.normalize().scale(max) : v; }

    /** Limit yaw and acceleration separately; an about-face becomes an arc, never a zero-speed stall. */
    public static Vec3 steer(Vec3 current, Vec3 desired, double speed, int bank) {
        Vec3 forward = horizontal(current), to = horizontal(desired);
        double cross = forward.x * to.z - forward.z * to.x;
        double angle = Math.atan2(cross, forward.dot(to));
        if (Math.abs(cross) < 1e-6 && forward.dot(to) < 0) angle = bank * Math.PI;
        angle = Math.clamp(angle, -.13, .13);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        Vec3 heading = new Vec3(forward.x * cos - forward.z * sin, 0, forward.x * sin + forward.z * cos);
        double pitch = Math.clamp(desired.y / Math.max(1, desired.length()), -.45, .45);
        double nextSpeed = Math.clamp(speed, Math.max(0, current.length() - .08), current.length() + .045);
        // Dragons need enough vertical authority to make a pass through a flying player;
        // the former 0.035 cap caused a very slow, visibly indecisive climb/descent.
        double vertical = Math.clamp(pitch * nextSpeed, current.y - .12, current.y + .12);
        return limit(heading.scale(Math.sqrt(Math.max(0, nextSpeed * nextSpeed - vertical * vertical))).add(0, vertical, 0), speed);
    }
}
