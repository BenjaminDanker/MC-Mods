package com.silver.arsenal;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** How the arsenal hurts things. Everything is credited to the player who used the weapon. */
final class Dmg {
    /** Share of an explosion's damage the thrower takes, so a rocket jump is possible but not free. */
    static final double SELF_SHARE = 0.35;

    private Dmg() {
    }

    static boolean living(net.minecraft.world.entity.Entity entity) {
        return entity instanceof LivingEntity target && target.isAlive() && !target.isSpectator();
    }

    /** A direct hit. {@code ignoreIframes} lets a fast weapon land every shot instead of losing some to the invulnerability window. */
    static boolean shoot(ServerLevel level, ServerPlayer owner, LivingEntity target, double amount, boolean ignoreIframes) {
        if (amount <= 0) {
            return false;
        }
        if (ignoreIframes) {
            target.invulnerableTime = 0;
        }
        return target.hurtServer(level, level.damageSources().playerAttack(owner), (float) amount);
    }

    /** Extra damage that is not a weapon swing: procs, bleed, arcs. A player attack, so armor applies. */
    static boolean proc(ServerLevel level, ServerPlayer owner, LivingEntity target, double amount) {
        if (amount <= 0) {
            return false;
        }
        target.invulnerableTime = 0;
        return target.hurtServer(level, level.damageSources().playerAttack(owner), (float) amount);
    }

    /** Throws a living thing away from {@code from}, as a shove of the given strength, with a little lift. */
    static void shove(LivingEntity target, Vec3 from, double strength, double lift) {
        Vec3 away = target.position().subtract(from);
        Vec3 flat = new Vec3(away.x, 0, away.z);
        Vec3 push = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : flat.normalize().scale(strength);
        target.setDeltaMovement(target.getDeltaMovement().add(push.x, lift, push.z));
        target.hurtMarked = true;
    }

    /** Damage in a sphere with falloff to the edge, no block damage. Returns how many things were hurt. */
    static int blast(ServerLevel level, ServerPlayer owner, Vec3 at, double radius, double damage, double knock) {
        AABB box = new AABB(at, at).inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living);
        DamageSource source = level.damageSources().explosion(owner, owner);
        int hurt = 0;
        for (LivingEntity target : targets) {
            double distance = target.getBoundingBox().getCenter().distanceTo(at);
            if (distance > radius) {
                continue;
            }
            double factor = 1.0 - 0.6 * (distance / radius);
            double amount = damage * factor * (target == owner ? SELF_SHARE : 1.0);
            target.invulnerableTime = 0;
            if (target.hurtServer(level, source, (float) amount)) {
                hurt++;
            }
            shove(target, at, knock * factor, 0.25 * knock);
        }
        return hurt;
    }
}
