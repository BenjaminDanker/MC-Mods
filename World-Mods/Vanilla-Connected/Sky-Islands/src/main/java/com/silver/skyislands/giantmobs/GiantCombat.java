package com.silver.skyislands.giantmobs;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The fighting brain of the stationary sky-island giants.
 *
 * Giants cannot move (they would walk off their island), so every attack is telegraphed and has a
 * counter the player can learn:
 *
 *  - Boulder volley: ranged salvo, aimed with a partial lead on the target. Keep moving sideways.
 *  - Ground slam: shockwave around the giant. Jump when the ring closes; players in the air are unharmed.
 *  - Rock rain: boulders on a marked circle under anyone who stands still. Move off the circle.
 *
 * Below the enrage threshold the gaps shrink, volleys grow, slams widen and are followed by a volley,
 * and rock rain marks every stationary player.
 */
final class GiantCombat {
    private static final int OPENING_DELAY_TICKS = 50;
    private static final double STILL_RADIUS = 1.5;
    private static final int SALVO_SPACING_TICKS = 2;
    private static final int RAIN_SPREAD_TICKS = 24;
    private static final DustParticleOptions WARNING = new DustParticleOptions(0xFF3A1E, 1.6F);
    private static final DustParticleOptions WARNING_HOT = new DustParticleOptions(0xFFC800, 1.8F);

    private enum Move { VOLLEY, SLAM, RAIN }

    /** One boulder of a salvo waiting for its tick. */
    private record Shot(long fireTick, Vec3 target) {
    }

    private static final class Brain {
        Move active;
        long startedAt;
        long resolveAt;
        UUID aimPlayer;
        Vec3 aimPoint;
        boolean enraged;
        Move queued;
        long nextAttackAt;
        final EnumMap<Move, Long> readyAt = new EnumMap<>(Move.class);
        final ArrayDeque<Shot> salvo = new ArrayDeque<>();
        /** Rock rain boulders waiting to fall; spread over a second so they cannot land as one blow. */
        final ArrayDeque<Shot> rain = new ArrayDeque<>();

        Brain(long firstAttackAt) {
            nextAttackAt = firstAttackAt;
        }

        boolean ready(Move move, long now) {
            return now >= readyAt.getOrDefault(move, 0L);
        }
    }

    /** What the giants need to know about a player, updated once per tick. */
    private static final class Motion {
        Vec3 lastPos;
        Vec3 velocity = Vec3.ZERO;
        Vec3 stillAnchor;
        long stillSince;
    }

    private final GiantMobsConfig config;
    private final GiantProjectiles projectiles;
    private final Map<UUID, Brain> brains = new HashMap<>();
    private final Map<UUID, Motion> motions = new HashMap<>();
    private final Random random = new Random();

    GiantCombat(GiantMobsConfig config, GiantProjectiles projectiles) {
        this.config = config;
        this.projectiles = projectiles;
    }

    void forget(UUID giantId) {
        brains.remove(giantId);
    }

    /** Samples every player's velocity and how long they have stood still. Call once per tick. */
    void trackPlayers(ServerLevel world, long now) {
        motions.keySet().removeIf(id -> world.getPlayerByUUID(id) == null);
        for (ServerPlayer player : world.players()) {
            Motion motion = motions.computeIfAbsent(player.getUUID(), id -> new Motion());
            Vec3 pos = player.position();
            if (motion.lastPos != null) {
                Vec3 step = pos.subtract(motion.lastPos);
                motion.velocity = motion.velocity.scale(0.5).add(step.scale(0.5));
            }
            motion.lastPos = pos;
            if (motion.stillAnchor == null || pos.distanceToSqr(motion.stillAnchor) > STILL_RADIUS * STILL_RADIUS) {
                motion.stillAnchor = pos;
                motion.stillSince = now;
            }
        }
    }

    void tick(ServerLevel world, Giant giant, UUID id, List<ServerPlayer> inRange, long now) {
        Brain brain = brains.computeIfAbsent(id, key -> new Brain(now + OPENING_DELAY_TICKS));
        updateEnrage(world, giant, brain);
        fireDueShots(world, giant, brain, now);

        if (inRange.isEmpty()) {
            brain.active = null;
            return;
        }

        if (brain.active != null) {
            continueAttack(world, giant, brain, inRange, now);
            return;
        }

        ServerPlayer nearest = nearest(giant, inRange);
        faceTarget(giant, nearest.position());
        if (now >= brain.nextAttackAt) {
            chooseAttack(world, giant, brain, inRange, nearest, now);
        }
    }

    // ---- choosing -------------------------------------------------------------------------------

    private void chooseAttack(ServerLevel world, Giant giant, Brain brain, List<ServerPlayer> inRange,
            ServerPlayer nearest, long now) {
        if (brain.queued != null) {
            Move queued = brain.queued;
            brain.queued = null;
            if (queued == Move.VOLLEY && nearest.distanceTo(giant) >= config.volleyMinRangeBlocks) {
                startVolley(world, giant, brain, pickTarget(giant, inRange), now);
                return;
            }
        }

        double slamReach = slamRadius(brain) * 0.85;
        if (brain.ready(Move.SLAM, now) && horizontalDistance(giant, nearest) <= slamReach
                && Math.abs(nearest.getY() - giant.getY()) <= 8.0) {
            startSlam(world, giant, brain, now);
            return;
        }

        if (brain.ready(Move.RAIN, now)) {
            ServerPlayer still = stationaryTarget(giant, inRange, now);
            if (still != null) {
                startRain(world, giant, brain, still, inRange, now);
                return;
            }
        }

        if (brain.ready(Move.VOLLEY, now)) {
            List<ServerPlayer> far = new ArrayList<>();
            for (ServerPlayer player : inRange) {
                if (player.distanceTo(giant) >= config.volleyMinRangeBlocks) {
                    far.add(player);
                }
            }
            if (!far.isEmpty()) {
                startVolley(world, giant, brain, far.get(random.nextInt(far.size())), now);
            }
        }
    }

    private ServerPlayer stationaryTarget(Giant giant, List<ServerPlayer> inRange, long now) {
        ServerPlayer best = null;
        long bestSince = Long.MAX_VALUE;
        for (ServerPlayer player : inRange) {
            Motion motion = motions.get(player.getUUID());
            if (motion == null || now - motion.stillSince < config.rainStillTicks) {
                continue;
            }
            double distance = horizontalDistance(giant, player);
            if (distance < 6.0 || motion.stillSince >= bestSince) {
                continue;
            }
            bestSince = motion.stillSince;
            best = player;
        }
        return best;
    }

    private ServerPlayer pickTarget(Giant giant, List<ServerPlayer> inRange) {
        return inRange.get(random.nextInt(inRange.size()));
    }

    // ---- starting -------------------------------------------------------------------------------

    private void startVolley(ServerLevel world, Giant giant, Brain brain, ServerPlayer target, long now) {
        brain.active = Move.VOLLEY;
        brain.startedAt = now;
        brain.resolveAt = now + config.volleyWindupTicks;
        brain.aimPlayer = target.getUUID();
        play(world, giant.position(), SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.7F);
    }

    private void startSlam(ServerLevel world, Giant giant, Brain brain, long now) {
        brain.active = Move.SLAM;
        brain.startedAt = now;
        brain.resolveAt = now + config.slamWindupTicks;
        play(world, giant.position(), SoundEvents.ENDER_DRAGON_GROWL, 3.5F, 0.5F);
    }

    private void startRain(ServerLevel world, Giant giant, Brain brain, ServerPlayer first,
            List<ServerPlayer> inRange, long now) {
        brain.active = Move.RAIN;
        brain.startedAt = now;
        brain.resolveAt = now + config.rainWindupTicks;
        brain.aimPlayer = first.getUUID();
        brain.aimPoint = first.position();
        play(world, giant.position(), SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.9F);
    }

    // ---- telegraph and resolve ------------------------------------------------------------------

    private void continueAttack(ServerLevel world, Giant giant, Brain brain, List<ServerPlayer> inRange, long now) {
        Move move = brain.active;
        long elapsed = now - brain.startedAt;
        switch (move) {
            case VOLLEY -> {
                ServerPlayer target = findPlayer(world, brain.aimPlayer);
                if (target == null || !inRange.contains(target)) {
                    cancel(giant, brain, now, config.attackGapTicks);
                    return;
                }
                // Track the target until the last moment, so the throw is aimed where they are heading.
                faceTarget(giant, target.position());
                if (elapsed % 4 == 0) {
                    marker(world, predict(giant, target), (float) config.volleySpreadBlocks + 1.0F, elapsed > config.volleyWindupTicks * 0.6);
                }
                if (now >= brain.resolveAt) {
                    resolveVolley(world, giant, brain, target, now);
                }
            }
            case SLAM -> {
                if (elapsed % 3 == 0) {
                    slamWarning(world, giant, brain, elapsed);
                }
                if (now >= brain.resolveAt) {
                    resolveSlam(world, giant, brain, now);
                }
            }
            case RAIN -> {
                if (elapsed % 4 == 0) {
                    rainWarning(world, brain, elapsed);
                }
                if (now >= brain.resolveAt) {
                    resolveRain(world, giant, brain, inRange, now);
                }
            }
        }
    }

    private void resolveVolley(ServerLevel world, Giant giant, Brain brain, ServerPlayer target, long now) {
        Vec3 center = predict(giant, target);
        int count = config.volleyBoulders + (brain.enraged ? 3 : 0);
        Vec3 delta = center.subtract(giant.position());
        Vec3 lateral = new Vec3(-delta.z, 0.0, delta.x).normalize();
        double spread = config.volleySpreadBlocks;
        for (int i = 0; i < count; i++) {
            // A fan across the target with the middle boulder first, so weaving sideways is what saves you.
            double side = count == 1 ? 0.0 : ((double) i / (count - 1) * 2.0 - 1.0) * spread * 2.0;
            Vec3 aim = center.add(lateral.scale(side)).add(0.0, 0.0, 0.0);
            long fireTick = now + (long) Math.abs(i - count / 2) * SALVO_SPACING_TICKS;
            brain.salvo.add(new Shot(fireTick, aim));
        }
        play(world, giant.position(), SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
        finish(brain, Move.VOLLEY, config.volleyCooldownTicks, now);
    }

    private void resolveSlam(ServerLevel world, Giant giant, Brain brain, long now) {
        double radius = slamRadius(brain);
        Vec3 center = giant.position();
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double distance = horizontalDistance(giant, player);
            if (distance > radius || Math.abs(player.getY() - giant.getY()) > 8.0) {
                continue;
            }
            // The counter: be off the ground when the wave passes.
            if (!player.onGround()) {
                continue;
            }
            float damage = (float) (config.slamDamage * (distance <= radius * 0.5 ? 1.0 : 0.65));
            player.hurtServer(world, world.damageSources().mobAttack(giant), damage);
            player.knockback(1.3, center.x - player.getX(), center.z - player.getZ(), world.damageSources().mobAttack(giant), 0.0F);
            player.push(0.0, 0.5, 0.0);
            player.hurtMarked = true;
        }
        ring(world, center, radius, ParticleTypes.EXPLOSION, 36);
        ring(world, center, radius * 0.6, ParticleTypes.POOF, 24);
        play(world, center, SoundEvents.GENERIC_EXPLODE, 4.0F, 0.5F);
        play(world, center, SoundEvents.ANVIL_LAND, 3.0F, 0.4F);
        finish(brain, Move.SLAM, config.slamCooldownTicks, now);
        if (brain.enraged) {
            brain.queued = Move.VOLLEY;
            brain.nextAttackAt = now + 14;
        }
    }

    private void resolveRain(ServerLevel world, Giant giant, Brain brain, List<ServerPlayer> inRange, long now) {
        List<Vec3> centers = new ArrayList<>();
        centers.add(brain.aimPoint);
        if (brain.enraged) {
            for (ServerPlayer player : inRange) {
                Motion motion = motions.get(player.getUUID());
                if (centers.size() < 3 && motion != null && now - motion.stillSince >= config.rainStillTicks / 2
                        && centers.stream().noneMatch(c -> c.distanceToSqr(player.position()) < 16.0)) {
                    centers.add(player.position());
                }
            }
        }
        for (Vec3 center : centers) {
            for (int i = 0; i < config.rainBoulders; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = Math.sqrt(random.nextDouble()) * config.rainRadiusBlocks;
                Vec3 from = new Vec3(center.x + Math.cos(angle) * radius, center.y + 14.0 + random.nextDouble() * 4.0,
                        center.z + Math.sin(angle) * radius);
                brain.rain.add(new Shot(now + random.nextInt(RAIN_SPREAD_TICKS), from));
            }
        }
        play(world, giant.position(), SoundEvents.GENERIC_EXPLODE, 2.5F, 0.8F);
        finish(brain, Move.RAIN, config.rainCooldownTicks, now);
    }

    private void fireDueShots(ServerLevel world, Giant giant, Brain brain, long now) {
        while (!brain.salvo.isEmpty() && brain.salvo.peekFirst().fireTick() <= now) {
            Shot shot = brain.salvo.pollFirst();
            double yaw = Math.toRadians(giant.getYRot());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            Vec3 start = new Vec3(giant.getX(), giant.getEyeY() - 0.5, giant.getZ()).add(forward.scale(2.8));
            int flight = projectiles.lob(world, giant, start, shot.target(), now);
            if (flight >= 0) {
                play(world, start, SoundEvents.IRON_GOLEM_ATTACK, 2.0F, 0.6F);
            }
        }
        brain.rain.removeIf(shot -> {
            if (shot.fireTick() > now) {
                return false;
            }
            projectiles.drop(world, giant, shot.target(), now);
            return true;
        });
        // Attacks of a giant that is gone must not fire later from a stale brain.
        if (giant.isRemoved() || !giant.isAlive()) {
            brain.salvo.clear();
            brain.rain.clear();
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private void finish(Brain brain, Move move, int cooldown, long now) {
        brain.readyAt.put(move, now + scaledTicks(brain, cooldown));
        brain.active = null;
        brain.nextAttackAt = now + scaledTicks(brain, config.attackGapTicks);
    }

    private void cancel(Giant giant, Brain brain, long now, int gap) {
        brain.active = null;
        brain.nextAttackAt = now + gap;
    }

    private long scaledTicks(Brain brain, int ticks) {
        return brain.enraged ? Math.max(5L, Math.round(ticks * config.enrageGapScale)) : ticks;
    }

    private double slamRadius(Brain brain) {
        return config.slamRadiusBlocks + (brain.enraged ? 3 : 0);
    }

    private void updateEnrage(ServerLevel world, Giant giant, Brain brain) {
        boolean enraged = giant.getHealth() <= giant.getMaxHealth() * config.enrageHealthFraction;
        if (enraged && !brain.enraged) {
            play(world, giant.position(), SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.4F);
            world.sendParticles(ParticleTypes.ANGRY_VILLAGER, giant.getX(), giant.getEyeY(), giant.getZ(), 24, 1.5, 1.0, 1.5, 0.0);
        }
        brain.enraged = enraged;
    }

    /** Where to aim: the target's position plus a partial lead along their current velocity. */
    private Vec3 predict(Giant giant, ServerPlayer target) {
        Motion motion = motions.get(target.getUUID());
        Vec3 pos = new Vec3(target.getX(), target.getEyeY() - 0.2, target.getZ());
        if (motion == null || config.volleyLeadFactor <= 0.0) {
            return pos;
        }
        double flightTicks = Mth.clamp(target.distanceTo(giant) / 1.6, 8.0, 40.0);
        Vec3 lead = new Vec3(motion.velocity.x, 0.0, motion.velocity.z).scale(flightTicks * config.volleyLeadFactor);
        if (lead.lengthSqr() > 14.0 * 14.0) {
            lead = lead.normalize().scale(14.0);
        }
        return pos.add(lead);
    }

    private void marker(ServerLevel world, Vec3 center, float radius, boolean hot) {
        ring(world, new Vec3(center.x, center.y - 1.2, center.z), radius, hot ? WARNING_HOT : WARNING, 16);
    }

    private void slamWarning(ServerLevel world, Giant giant, Brain brain, long elapsed) {
        double radius = slamRadius(brain);
        boolean closing = elapsed > config.slamWindupTicks * 0.6;
        ring(world, giant.position().add(0.0, 0.15, 0.0), radius, closing ? WARNING_HOT : WARNING, (int) (radius * 3));
        ring(world, giant.position().add(0.0, 0.15, 0.0), radius * 0.5, WARNING, (int) (radius * 1.5));
    }

    private void rainWarning(ServerLevel world, Brain brain, long elapsed) {
        boolean closing = elapsed > config.rainWindupTicks * 0.6;
        ring(world, brain.aimPoint.add(0.0, 0.15, 0.0), config.rainRadiusBlocks, closing ? WARNING_HOT : WARNING, config.rainRadiusBlocks * 5);
    }

    private static void ring(ServerLevel world, Vec3 center, double radius, ParticleOptions particle, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            world.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void play(ServerLevel world, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void play(ServerLevel world, Vec3 pos, net.minecraft.core.Holder<SoundEvent> sound, float volume, float pitch) {
        play(world, pos, sound.value(), volume, pitch);
    }

    private static ServerPlayer findPlayer(ServerLevel world, UUID id) {
        return id == null ? null : world.getServer().getPlayerList().getPlayer(id);
    }

    private static ServerPlayer nearest(Giant giant, List<ServerPlayer> players) {
        ServerPlayer best = players.get(0);
        double bestSq = best.distanceToSqr(giant);
        for (ServerPlayer player : players) {
            double sq = player.distanceToSqr(giant);
            if (sq < bestSq) {
                bestSq = sq;
                best = player;
            }
        }
        return best;
    }

    private static double horizontalDistance(Giant giant, ServerPlayer player) {
        double dx = player.getX() - giant.getX();
        double dz = player.getZ() - giant.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void faceTarget(Giant giant, Vec3 targetPos) {
        Vec3 delta = targetPos.subtract(giant.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(horizontal, 1.0e-6)));
        giant.setYRot(yaw);
        giant.setYHeadRot(yaw);
        giant.setYBodyRot(yaw);
        giant.setXRot(Mth.clamp(pitch, -30.0F, 45.0F));
    }
}
