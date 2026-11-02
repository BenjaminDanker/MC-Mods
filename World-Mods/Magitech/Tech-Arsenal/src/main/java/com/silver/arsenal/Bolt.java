package com.silver.arsenal;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Things that fly: grenades, rockets, flares, harpoons and the thrown gadgets. The projectile is a snowball showing the round's
 * model; where it stops, the weapon's impact (blast, flare, harpoon, smoke, EMP) happens.
 */
final class Bolt implements Action {
    @Override
    public boolean use(Ctx c) {
        Params p = c.p();
        ServerPlayer player = c.player();
        ServerLevel level = c.level();
        var projectileItem = Ammo.item(p.str("projectile", ""));
        Snowball ball = new Snowball(level, player, new ItemStack(projectileItem));
        double speed = p.num("speed", 1.2);
        double spread = p.num("spread", 0);
        double gravity = p.num("gravity", 1.0);
        ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, (float) speed, (float) spread);
        ball.setNoGravity(gravity == 0.0);
        level.addFreshEntity(ball);
        Fx.sound(level, player.position(), p.str("impact", "").equals("blast") ? SoundEvents.FIRECHARGE_USE : SoundEvents.SNOWBALL_THROW, 0.8F, 0.8F);
        Engine.add(new Flight(ball, level, player, gravity, Flight.trail(p.str("trail", "")), impact(c)));
        return true;
    }

    private static Flight.Impact impact(Ctx c) {
        Params p = c.p();
        return switch (p.str("impact", "")) {
            case "blast" -> (level, owner, at) -> blast(c, level, owner, at);
            case "flare" -> (level, owner, at) -> flare(c, level, owner, at);
            case "harpoon" -> (level, owner, at) -> harpoon(c, level, owner, at);
            case "smoke" -> (level, owner, at) -> Zones.smoke(level, owner, at, p.child("smoke"));
            case "emp" -> (level, owner, at) -> emp(level, owner, at, p.child("emp"));
            default -> (level, owner, at) -> { };
        };
    }

    private static void blast(Ctx c, ServerLevel level, ServerPlayer owner, Vec3 at) {
        Params blast = c.p().child("blast");
        double radius = blast.num("radius", 3);
        Dmg.blast(level, owner, at, radius, blast.num("damage", 6) * (c.tempered() ? Tuning.temperedDamage : 1.0), blast.num("knock", 0.8));
        Fx.bang(level, at, radius);
    }

    /** The creature the projectile struck: the nearest living thing to where it stopped, other than its owner. */
    static LivingEntity struck(ServerLevel level, ServerPlayer owner, Vec3 at, double reach) {
        AABB box = new AABB(at, at).inflate(reach);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
            if (candidate == owner) {
                continue;
            }
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(at);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void flare(Ctx c, ServerLevel level, ServerPlayer owner, Vec3 at) {
        Params flare = c.p().child("flare");
        double radius = flare.num("radius", 2.5);
        LivingEntity direct = struck(level, owner, at, 1.8);
        if (direct != null) {
            Dmg.shoot(level, owner, direct, flare.num("damage", 2.5) * (c.tempered() ? Tuning.temperedDamage : 1.0), true);
            direct.igniteForTicks(flare.integer("ignite", 100));
        }
        AABB box = new AABB(at, at).inflate(radius);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
            if (near != owner) {
                Fx.applyEffect(near, "glowing", flare.integer("glow", 160), 0);
            }
        }
        Fx.burst(level, ParticleTypes.FLAME, at, 40, 0.5, 0.08);
        Fx.burst(level, ParticleTypes.FIREWORK, at, 20, 0.4, 0.1);
        Fx.sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 1.0F);
    }

    private static void harpoon(Ctx c, ServerLevel level, ServerPlayer owner, Vec3 at) {
        Params harpoon = c.p().child("harpoon");
        LivingEntity target = struck(level, owner, at, 1.8);
        if (target == null) {
            Fx.burst(level, ParticleTypes.CRIT, at, 6, 0.1, 0.1);
            return;
        }
        Dmg.shoot(level, owner, target, harpoon.num("damage", 5) * (c.tempered() ? Tuning.temperedDamage : 1.0), true);
        Vec3 toOwner = owner.getEyePosition().subtract(target.getBoundingBox().getCenter());
        double pull = harpoon.num("pull", 1.0);
        Vec3 haul = toOwner.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toOwner.normalize().scale(pull + Math.min(1.2, toOwner.length() * 0.08));
        target.setDeltaMovement(haul.x, Math.max(0.3, haul.y + 0.25), haul.z);
        target.hurtMarked = true;
        Fx.line(level, ParticleTypes.CRIT, owner.getEyePosition().add(0, -0.3, 0), target.getBoundingBox().getCenter(), 0.5);
        Fx.sound(level, target.position(), SoundEvents.CHAIN_HIT, 1.0F, 0.7F);
    }

    private static void emp(ServerLevel level, ServerPlayer owner, Vec3 at, Params emp) {
        double radius = emp.num("radius", 6.5);
        long ms = (long) emp.num("bender_ms", 6000);
        int slow = emp.integer("slow_ticks", 60);
        AABB box = new AABB(at, at).inflate(radius);
        Map<String, Integer> reached = new HashMap<>();
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
            if (near.getBoundingBox().getCenter().distanceTo(at) > radius) {
                continue;
            }
            Fx.applyEffect(near, "slowness", slow, 1);
            reached.merge("creatures", 1, Integer::sum);
            if (near instanceof ServerPlayer player && BendingLink.disrupt(player, ms)) {
                reached.merge("benders", 1, Integer::sum);
            }
        }
        Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, at, 120, radius / 2, 0.4);
        Fx.sound(level, at, SoundEvents.BEACON_DEACTIVATE, 1.4F, 1.6F);
        org.slf4j.LoggerFactory.getLogger("tech-arsenal").info("EMP at {} reached {}", at, reached);
    }
}
