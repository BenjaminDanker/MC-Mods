package com.silver.skyislands.giantmobs;

import com.silver.skyislands.giantmobs.mixins.FallingBlockEntityInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thrown and dropped boulders: launch solver, flight tracking and impact damage. */
final class GiantProjectiles {
    static final String TAG = "sky_islands_managed_giant_projectile";

    private static final double DRAG = 0.98D;
    private static final double GRAVITY = 0.04D;
    private static final int MIN_FLIGHT_TICKS = 8;
    private static final int EXTRA_LIFE_TICKS = 12;
    private static final int MAX_DROP_LIFE_TICKS = 80;

    private record Flight(long expiresTick, Vec3 launchPos, double maxTravelSq, float damage) {
    }

    private record Launch(Vec3 velocity, int flightTicks) {
    }

    private final GiantMobsConfig config;
    private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();

    GiantProjectiles(GiantMobsConfig config) {
        this.config = config;
    }

    static boolean isProjectile(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    int activeCount() {
        return flights.size();
    }

    List<UUID> activeIds() {
        return new ArrayList<>(flights.keySet());
    }

    /** Lobs a boulder along an arc that lands on {@code target}. Returns the flight time in ticks, or -1. */
    int lob(ServerLevel world, Giant giant, Vec3 start, Vec3 target, long nowTick) {
        Launch launch = solve(start, target, config.attackRangeBlocks + 16);
        if (launch == null) {
            return -1;
        }
        FallingBlockEntity boulder = create(world, giant, start, boulderBlock(world, giant));
        boulder.setDeltaMovement(launch.velocity());
        if (!world.addFreshEntity(boulder)) {
            return -1;
        }
        long expires = nowTick + launch.flightTicks() + EXTRA_LIFE_TICKS;
        double reach = (double) config.attackRangeBlocks + 24.0;
        flights.put(boulder.getUUID(), new Flight(expires, start, reach * reach, config.projectileImpactDamage));
        return launch.flightTicks();
    }

    /** Drops a boulder straight down from {@code from}. */
    boolean drop(ServerLevel world, Giant giant, Vec3 from, long nowTick) {
        FallingBlockEntity boulder = create(world, giant, from, boulderBlock(world, giant));
        boulder.setDeltaMovement(Vec3.ZERO);
        if (!world.addFreshEntity(boulder)) {
            return false;
        }
        flights.put(boulder.getUUID(), new Flight(nowTick + MAX_DROP_LIFE_TICKS, from, Double.MAX_VALUE, config.rainDamage));
        return true;
    }

    private FallingBlockEntity create(ServerLevel world, Giant giant, Vec3 pos, BlockState state) {
        // FallingBlockEntity.fall would also add the entity and replace the block at pos with air; the caller adds it.
        FallingBlockEntity boulder = FallingBlockEntityInvoker.skyIslands$create(world, pos.x, pos.y, pos.z, state);
        boulder.disableDrop();
        boulder.dropItem = false;
        boulder.time = 1;
        boulder.addTag(TAG);
        boulder.setStartPos(giant.blockPosition());
        return boulder;
    }

    /** Boulders are made of whatever the giant stands on, so every island throws its own material. */
    private static BlockState boulderBlock(ServerLevel world, Giant giant) {
        BlockPos below = giant.blockPosition().below();
        BlockState state = world.getBlockState(below);
        if (state.isAir() || state.getCollisionShape(world, below).isEmpty() || !state.isFaceSturdy(world, below, Direction.UP)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return state;
    }

    void tick(ServerLevel world, long nowTick) {
        Iterator<Map.Entry<UUID, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Flight> entry = iterator.next();
            Entity entity = world.getEntity(entry.getKey());
            if (!(entity instanceof FallingBlockEntity boulder) || boulder.isRemoved()) {
                iterator.remove();
                continue;
            }
            Flight flight = entry.getValue();
            boolean spent = flight.expiresTick() <= nowTick
                    || boulder.position().distanceToSqr(flight.launchPos()) > flight.maxTravelSq()
                    || boulder.onGround();
            if (!spent && hitSomething(world, boulder, flight.damage())) {
                spent = true;
            }
            if (spent) {
                splash(world, boulder, flight.damage());
                iterator.remove();
            }
        }
    }

    private boolean hitSomething(ServerLevel world, FallingBlockEntity boulder, float damage) {
        boolean hit = false;
        // Boulders move several blocks per tick, so test everything along this tick's path, not just where they are.
        AABB box = boulder.getBoundingBox().expandTowards(boulder.getDeltaMovement()).inflate(0.8);
        for (Entity candidate : world.getEntities(boulder, box, GiantProjectiles::isVictim)) {
            LivingEntity living = (LivingEntity) candidate;
            if (strike(world, boulder, living, damage, config.projectileKnockbackStrength)) {
                hit = true;
            }
        }
        return hit;
    }

    private void splash(ServerLevel world, FallingBlockEntity boulder, float damage) {
        if (boulder.isRemoved()) {
            return;
        }
        AABB box = boulder.getBoundingBox().inflate(1.6);
        for (Entity candidate : world.getEntities(boulder, box, GiantProjectiles::isVictim)) {
            strike(world, boulder, (LivingEntity) candidate, Math.max(1.0F, damage * 0.5F),
                    config.projectileKnockbackStrength * 0.65);
        }
        boulder.discard();
    }

    /**
     * Armor, Protection and Resistance all apply: the base damage is simply set high enough that netherite still
     * feels it. Invulnerability frames make overlapping boulders count once, which is what stops a rain of rocks
     * from adding up to a one-shot.
     */
    private static boolean strike(ServerLevel world, FallingBlockEntity boulder, LivingEntity victim, float damage, double knockback) {
        if (!victim.hurtServer(world, world.damageSources().mobProjectile(boulder, null), damage)) {
            return false;
        }
        victim.knockback(knockback, boulder.getX() - victim.getX(), boulder.getZ() - victim.getZ(),
                world.damageSources().fallingBlock(boulder), 0.0F);
        return true;
    }

    private static boolean isVictim(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Giant) && entity.isAlive();
    }

    /**
     * Finds the gentlest launch (low arc, moderate speed) that lands on {@code target} after the same
     * drag and gravity the falling block entity applies each tick.
     */
    private Launch solve(Vec3 start, Vec3 target, int maxHorizontalRange) {
        Vec3 delta = target.subtract(start);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance < 1.0e-6 || horizontalDistance > maxHorizontalRange) {
            return null;
        }

        int maxFlightTicks = Math.max(40, Math.min(200, config.attackRangeBlocks + 20));
        double preferredMaxSpeed = Math.max(3.0, maxHorizontalRange / 24.0);
        double bestScore = Double.POSITIVE_INFINITY;
        Launch best = null;

        double sumCoeff = 0.0;
        double coeff = 1.0;
        double gravityVelocity = 0.0;
        double gravityDisplacement = 0.0;
        for (int ticks = 1; ticks <= maxFlightTicks; ticks++) {
            sumCoeff += coeff;
            gravityVelocity -= GRAVITY;
            gravityDisplacement += gravityVelocity;
            gravityVelocity *= DRAG;
            coeff *= DRAG;
            if (ticks < MIN_FLIGHT_TICKS) {
                continue;
            }

            double vx = delta.x / sumCoeff;
            double vz = delta.z / sumCoeff;
            double vy = (delta.y - gravityDisplacement) / sumCoeff;
            double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
            double speed = Math.sqrt(horizontalSpeed * horizontalSpeed + vy * vy);
            double pitch = Math.abs(Math.atan2(vy, Math.max(horizontalSpeed, 1.0e-6)));

            double score = pitch * 4.0 + speed * 0.08 + ticks * 0.002;
            if (speed > preferredMaxSpeed) {
                score += (speed - preferredMaxSpeed) * 0.75;
            }
            if (score < bestScore) {
                bestScore = score;
                best = new Launch(new Vec3(vx, vy, vz), ticks);
            }
        }
        return best;
    }
}
