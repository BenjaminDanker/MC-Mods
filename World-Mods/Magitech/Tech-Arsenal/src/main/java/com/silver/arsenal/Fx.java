package com.silver.arsenal;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Particles, sounds and status effects, kept in one place so the weapons read as what they do. */
final class Fx {
    private static final Map<String, Holder<MobEffect>> EFFECTS = Map.ofEntries(
        Map.entry("slowness", MobEffects.SLOWNESS), Map.entry("weakness", MobEffects.WEAKNESS),
        Map.entry("blindness", MobEffects.BLINDNESS), Map.entry("glowing", MobEffects.GLOWING),
        Map.entry("nausea", MobEffects.NAUSEA), Map.entry("mining_fatigue", MobEffects.MINING_FATIGUE),
        Map.entry("darkness", MobEffects.DARKNESS), Map.entry("wither", MobEffects.WITHER),
        Map.entry("poison", MobEffects.POISON), Map.entry("hunger", MobEffects.HUNGER),
        Map.entry("regeneration", MobEffects.REGENERATION), Map.entry("resistance", MobEffects.RESISTANCE),
        Map.entry("absorption", MobEffects.ABSORPTION), Map.entry("speed", MobEffects.SPEED),
        Map.entry("slow_falling", MobEffects.SLOW_FALLING), Map.entry("jump_boost", MobEffects.JUMP_BOOST),
        Map.entry("strength", MobEffects.STRENGTH), Map.entry("levitation", MobEffects.LEVITATION));

    private Fx() {
    }

    static boolean applyEffect(LivingEntity target, String name, int ticks, int amplifier) {
        Holder<MobEffect> effect = EFFECTS.get(name);
        if (effect == null) {
            org.slf4j.LoggerFactory.getLogger("tech-arsenal").error("ALERT: unknown effect '{}' in arms.json", name);
            return false;
        }
        return target.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    /** Applies every {@code {"effect","ticks","amp"}} entry of a params list. */
    static void applyEffects(LivingEntity target, Params params) {
        for (Map<String, Object> entry : params.list("effects")) {
            Params effect = new Params(entry);
            applyEffect(target, effect.str("effect", ""), effect.integer("ticks", 40), effect.integer("amp", 0));
        }
    }

    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb, size);
    }

    /** A line of coloured dust between two points: the trail of a shot. */
    static void tracer(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size, double step) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        Vec3 direction = delta.scale(1.0 / length);
        DustParticleOptions particle = dust(rgb, size);
        for (double travelled = 0; travelled <= length; travelled += step) {
            Vec3 at = from.add(direction.scale(travelled));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        Vec3 direction = delta.scale(1.0 / length);
        for (double travelled = 0; travelled <= length; travelled += step) {
            Vec3 at = from.add(direction.scale(travelled));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void bang(ServerLevel level, Vec3 at, double radius) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, Math.max(1, (int) radius), radius / 3, radius / 3, radius / 3, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 12, radius / 3, radius / 3, radius / 3, 0.05);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }
}
