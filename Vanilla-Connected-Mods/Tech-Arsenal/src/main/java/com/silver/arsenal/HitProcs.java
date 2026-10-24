package com.silver.arsenal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The melee weapons' on-hit behaviours. */
final class HitProcs {
    /** Targets currently bleeding, so one target never carries a pile of overlapping bleeds from the same weapon. */
    private static final Map<UUID, Integer> BLEEDING = new HashMap<>();

    private HitProcs() {
    }

    static HitProc create(Params hit) {
        return switch (hit.kind()) {
            case "surge" -> HitProcs::surge;
            case "backstab" -> HitProcs::backstab;
            case "bleed" -> (level, attacker, target, spec, tempered) -> bleed(level, attacker, target, hit);
            case "stun" -> HitProcs::stun;
            case "arc" -> HitProcs::arc;
            case "piston" -> HitProcs::piston;
            case "rev" -> HitProcs::rev;
            default -> null;
        };
    }

    /** What one ordinary swing of this weapon deals, for proportional bonuses. */
    private static double swing(Spec spec, boolean tempered) {
        return new Params(spec.melee).num("damage", 1) * (tempered ? Tuning.temperedDamage : 1.0);
    }

    private static void surge(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered) {
        Params hit = new Params(spec.hit);
        if (Engine.everyNth(attacker.getUUID(), spec.id + ":surge", hit.integer("every", 4))) {
            Dmg.proc(level, attacker, target, swing(spec, tempered) * hit.num("bonus", 0.5));
            Fx.burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 14, 0.3, 0.3);
            Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.2F);
        }
    }

    private static void backstab(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered) {
        Vec3 toTarget = target.position().subtract(attacker.position());
        if (toTarget.lengthSqr() < 1.0e-4) {
            return;
        }
        // The target is facing away when it looks the same way the attacker is looking at it.
        if (toTarget.normalize().dot(target.getLookAngle()) > 0.25) {
            Dmg.proc(level, attacker, target, swing(spec, tempered) * (new Params(spec.hit).num("mult", 2.2) - 1.0));
            Fx.burst(level, ParticleTypes.DAMAGE_INDICATOR, target.getBoundingBox().getCenter(), 8, 0.2, 0.1);
            Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.5F);
        }
    }

    private static void bleed(ServerLevel level, ServerPlayer attacker, LivingEntity target, Params hit) {
        double damage = hit.num("damage", hit.num("bleed", 1.0));
        int pulses = hit.integer("pulses", 4);
        int every = Math.max(1, hit.integer("every", 20));
        if (BLEEDING.getOrDefault(target.getUUID(), 0) >= 2) {
            return;
        }
        BLEEDING.merge(target.getUUID(), 1, Integer::sum);
        Engine.add(new Task() {
            private int age;
            private int left = pulses;

            @Override
            public boolean tick(MinecraftServer server) {
                if (!target.isAlive() || target.isRemoved()) {
                    release();
                    return true;
                }
                if (++age % every == 0) {
                    Dmg.proc(level, attacker, target, damage);
                    Fx.burst(level, ParticleTypes.DAMAGE_INDICATOR, target.getBoundingBox().getCenter(), 3, 0.2, 0.0);
                    if (--left <= 0) {
                        release();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void cancel() {
                release();
            }

            private void release() {
                BLEEDING.computeIfPresent(target.getUUID(), (id, n) -> n <= 1 ? null : n - 1);
            }
        });
    }

    private static void stun(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered) {
        Params hit = new Params(spec.hit);
        if (Engine.everyNth(attacker.getUUID(), spec.id + ":stun", hit.integer("every", 3))) {
            Fx.applyEffect(target, "slowness", hit.integer("slow_ticks", 30), hit.integer("slow_amp", 2));
            Fx.applyEffect(target, "weakness", hit.integer("weak_ticks", 40), 0);
            Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, target.getBoundingBox().getCenter(), 25, 0.35, 0.3);
            Fx.sound(level, target.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 2.0F);
        }
    }

    private static void arc(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered) {
        Params hit = new Params(spec.hit);
        double radius = hit.num("radius", 4.5);
        double damage = swing(spec, tempered) * hit.num("frac", 0.4);
        int remaining = hit.integer("targets", 2);
        AABB box = target.getBoundingBox().inflate(radius);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
            if (remaining <= 0) {
                break;
            }
            if (other == target || other == attacker || other.distanceTo(target) > radius) {
                continue;
            }
            Fx.line(level, ParticleTypes.ELECTRIC_SPARK, target.getBoundingBox().getCenter(), other.getBoundingBox().getCenter(), 0.4);
            Dmg.proc(level, attacker, other, damage);
            remaining--;
        }
    }

    private static void piston(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered) {
        Params hit = new Params(spec.hit);
        if (Engine.everyNth(attacker.getUUID(), spec.id + ":piston", hit.integer("every", 5))) {
            Dmg.proc(level, attacker, target, hit.num("bonus", 3.0) * (tempered ? Tuning.temperedDamage : 1.0));
            Dmg.shove(target, attacker.position(), hit.num("knock", 1.6), 0.35);
            Fx.burst(level, ParticleTypes.EXPLOSION, target.getBoundingBox().getCenter(), 1, 0.0, 0.0);
            Fx.sound(level, target.position(), SoundEvents.PISTON_EXTEND, 1.0F, 1.2F);
        }
    }

    private static void rev(ServerLevel level, ServerPlayer attacker, LivingEntity target, Spec spec, boolean tempered) {
        if (!Engine.revving(attacker.getUUID())) {
            return;
        }
        Params hit = new Params(spec.hit);
        Dmg.proc(level, attacker, target, swing(spec, tempered) * hit.num("bonus", 0.25));
        bleed(level, attacker, target, hit);
        Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, target.getBoundingBox().getCenter(), 8, 0.25, 0.2);
    }
}
