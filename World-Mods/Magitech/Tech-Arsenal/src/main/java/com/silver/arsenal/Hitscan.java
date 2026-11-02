package com.silver.arsenal;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Guns that hit the instant they fire: pistol, shotgun (many pellets, damage fading with distance), rifles that pierce, and the
 * arc caster, whose bolt jumps from its target to others nearby.
 */
final class Hitscan implements Action {
    private static final boolean DEBUG = Boolean.getBoolean("arsenal.debug");

    @Override
    public boolean use(Ctx c) {
        Params p = c.p();
        ServerLevel level = c.level();
        ServerPlayer player = c.player();
        int pellets = Math.max(1, p.integer("pellets", 1));
        double damage = c.dmg("damage", 2.0);
        double range = p.num("range", 32);
        int pierce = p.integer("pierce", 0);
        double spread = p.num("spread", 0);
        double fallStart = p.num("falloff_start", range);
        double fallEnd = Math.max(fallStart + 0.01, p.num("falloff_end", range));
        double fallMin = p.num("falloff_min", 1.0);
        double knock = p.num("knock", 0);
        int ignite = p.integer("ignite", 0);
        boolean ignoreIframes = p.bool("no_iframes", true);
        int color = color(p.str("color", "FFFFFF"));
        Params chain = p.child("chain");

        Set<LivingEntity> struck = new HashSet<>();
        for (int pellet = 0; pellet < pellets; pellet++) {
            Vec3 direction = Shots.aim(player, spread);
            Vec3 eye = player.getEyePosition();
            Shots.Trace trace = Shots.trace(level, player, eye, direction, range, pierce, 0.15);
            if (DEBUG) {
                org.slf4j.LoggerFactory.getLogger("tech-arsenal").info("shot from {} dir {} end {} hitBlock={} hits={}", eye, direction, trace.end(), trace.hitBlock(),
                    trace.hits().stream().map(h -> h.entity().getName().getString() + "@" + String.format("%.1f", h.distance())).toList());
            }
            Fx.tracer(level, Shots.muzzle(player, direction), trace.end(), color, pellets > 1 ? 0.6F : 0.9F, pellets > 1 ? 1.2 : 0.7);
            boolean first = true;
            for (Shots.Hit hit : trace.hits()) {
                double fade = fallMin + (1.0 - fallMin) * (1.0 - clamp((hit.distance() - fallStart) / (fallEnd - fallStart)));
                if (Dmg.shoot(level, player, hit.entity(), damage * fade, ignoreIframes)) {
                    struck.add(hit.entity());
                    if (knock > 0) {
                        Dmg.shove(hit.entity(), player.position(), knock / pellets, 0.1);
                    }
                    if (ignite > 0) {
                        hit.entity().igniteForTicks(ignite);
                    }
                    Fx.applyEffects(hit.entity(), p);
                    Fx.burst(level, ParticleTypes.CRIT, hit.point(), 3, 0.1, 0.1);
                    if (first && chain.has("jumps")) {
                        arc(c, hit.entity(), damage * fade, chain);
                    }
                    first = false;
                }
            }
            if (trace.hitBlock() && trace.hits().isEmpty()) {
                Fx.burst(level, ParticleTypes.SMOKE, trace.end(), 2, 0.05, 0.01);
            }
        }
        sound(c, p.str("sound", "pistol"));
        return true;
    }

    /** The bolt leaves {@code from} for the nearest others within the radius, weaker each jump. */
    private static void arc(Ctx c, LivingEntity from, double damage, Params chain) {
        ServerLevel level = c.level();
        int jumps = chain.integer("jumps", 2);
        double radius = chain.num("radius", 5);
        double mult = chain.num("mult", 0.7);
        Set<LivingEntity> visited = new HashSet<>();
        visited.add(from);
        visited.add(c.player());
        LivingEntity current = from;
        double carried = damage;
        for (int jump = 0; jump < jumps; jump++) {
            LivingEntity next = nearest(level, current, radius, visited);
            if (next == null) {
                return;
            }
            carried *= mult;
            Fx.line(level, ParticleTypes.ELECTRIC_SPARK, current.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.4);
            Dmg.shoot(level, c.player(), next, carried, true);
            visited.add(next);
            current = next;
        }
    }

    private static LivingEntity nearest(ServerLevel level, LivingEntity around, double radius, Set<LivingEntity> excluded) {
        AABB box = around.getBoundingBox().inflate(radius);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
            if (excluded.contains(candidate)) {
                continue;
            }
            double distance = candidate.distanceToSqr(around);
            if (distance <= radius * radius && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void sound(Ctx c, String kind) {
        Vec3 at = c.player().position();
        SoundEvent sound;
        float pitch = 1.0F;
        switch (kind) {
            case "shotgun" -> {
                sound = SoundEvents.GENERIC_EXPLODE.value();
                pitch = 1.7F;
            }
            case "rifle" -> {
                sound = SoundEvents.CROSSBOW_SHOOT;
                pitch = 0.7F;
            }
            case "rail" -> {
                sound = SoundEvents.WARDEN_SONIC_BOOM;
                pitch = 1.6F;
            }
            case "arc" -> {
                sound = SoundEvents.LIGHTNING_BOLT_IMPACT;
                pitch = 2.0F;
            }
            case "taser" -> {
                sound = SoundEvents.LIGHTNING_BOLT_THUNDER;
                pitch = 2.0F;
            }
            default -> {
                sound = SoundEvents.CROSSBOW_SHOOT;
                pitch = 1.5F;
            }
        }
        Fx.sound(c.level(), at, sound, kind.equals("rail") ? 0.8F : 0.6F, pitch);
    }

    static int color(String hex) {
        try {
            return (int) Long.parseLong(hex.replace("#", ""), 16);
        } catch (NumberFormatException bad) {
            return 0xFFFFFF;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
