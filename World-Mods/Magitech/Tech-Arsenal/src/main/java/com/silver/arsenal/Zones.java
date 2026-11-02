package com.silver.arsenal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lingering areas: the smoke cloud. */
final class Zones {
    private Zones() {
    }

    static void smoke(ServerLevel level, ServerPlayer owner, Vec3 at, Params smoke) {
        double radius = smoke.num("radius", 5);
        int ticks = smoke.integer("ticks", 160);
        Fx.sound(level, at, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1.5F, 0.6F);
        Engine.add(new Task() {
            private int age;

            @Override
            public boolean tick(MinecraftServer server) {
                if (age % 3 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 1.0, at.z, 10, radius / 2.2, 1.0, radius / 2.2, 0.01);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 5, radius / 2.2, 0.6, radius / 2.2, 0.01);
                }
                if (age % 10 == 0) {
                    AABB box = new AABB(at, at).inflate(radius, 2.5, radius);
                    for (LivingEntity inside : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
                        if (inside != owner && inside.position().distanceTo(at) <= radius) {
                            Fx.applyEffect(inside, "blindness", 30, 0);
                            Fx.applyEffect(inside, "slowness", 30, 0);
                        }
                    }
                }
                return ++age >= ticks;
            }
        });
    }
}
