package com.silver.arsenal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Hit-scan: where a shot ends and what it passes through on the way. */
final class Shots {
    private Shots() {
    }

    record Hit(LivingEntity entity, Vec3 point, double distance) {
    }

    record Trace(Vec3 end, List<Hit> hits, boolean hitBlock) {
    }

    /** The shooter's look direction, bent by up to {@code spreadDegrees} in a random direction. */
    static Vec3 aim(LivingEntity shooter, double spreadDegrees) {
        Vec3 look = shooter.getLookAngle();
        if (spreadDegrees <= 0) {
            return look;
        }
        double spread = Math.tan(Math.toRadians(spreadDegrees));
        var random = shooter.getRandom();
        return look.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize();
    }

    /**
     * Follows a ray from {@code from} along {@code direction} for {@code range} blocks. It stops at the first solid block and
     * collects up to {@code pierce + 1} living things it crosses, nearest first.
     */
    static Trace trace(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double range, int pierce, double margin) {
        Vec3 end = from.add(direction.scale(range));
        var block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        boolean hitBlock = block.getType() != HitResult.Type.MISS;
        if (hitBlock) {
            end = block.getLocation();
        }
        AABB corridor = new AABB(from, end).inflate(1.5);
        List<Hit> hits = new ArrayList<>();
        for (Entity entity : level.getEntities(shooter, corridor, Dmg::living)) {
            AABB box = entity.getBoundingBox().inflate(margin);
            Optional<Vec3> point = box.clip(from, end);
            if (point.isPresent()) {
                hits.add(new Hit((LivingEntity) entity, point.get(), point.get().distanceTo(from)));
            } else if (box.contains(from)) {
                hits.add(new Hit((LivingEntity) entity, from, 0));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));
        if (hits.size() > pierce + 1) {
            hits = new ArrayList<>(hits.subList(0, pierce + 1));
        }
        return new Trace(end, hits, hitBlock);
    }

    /** Where a shot visibly leaves the weapon: just ahead of and below the eyes, toward the holding hand. */
    static Vec3 muzzle(LivingEntity shooter, Vec3 direction) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        double sideLength = side.length();
        Vec3 right = sideLength < 1.0e-4 ? Vec3.ZERO : side.scale(-0.22 / sideLength);
        return shooter.getEyePosition().add(direction.scale(0.9)).add(0, -0.18, 0).add(right);
    }
}
