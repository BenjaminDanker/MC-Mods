package com.silver.arsenal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A sustained cone of fire that follows the player's aim: the flame projector. */
final class Stream implements Action {
    @Override
    public boolean use(Ctx c) {
        Params p = c.p();
        ServerPlayer player = c.player();
        ServerLevel level = c.level();
        int duration = p.integer("duration", 36);
        int pulse = Math.max(1, p.integer("pulse", 3));
        double damage = c.dmg("damage", 1.1);
        double range = p.num("range", 7);
        double cosHalf = Math.cos(Math.toRadians(p.num("half_angle", 24)));
        int ignite = p.integer("ignite", 60);
        Engine.add(new Task() {
            private int age;

            @Override
            public boolean tick(MinecraftServer server) {
                if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                    return true;
                }
                Vec3 look = player.getLookAngle();
                Vec3 origin = Shots.muzzle(player, look);
                var random = player.getRandom();
                for (int i = 0; i < 7; i++) {
                    Vec3 flick = look.add(random.nextGaussian() * 0.12, random.nextGaussian() * 0.12, random.nextGaussian() * 0.12).normalize();
                    level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z, 0, flick.x, flick.y, flick.z, 0.55 + random.nextDouble() * 0.25);
                }
                if (age % 4 == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, origin.x + look.x * 2, origin.y + look.y * 2, origin.z + look.z * 2, 2, 0.2, 0.2, 0.2, 0.02);
                    Fx.sound(level, origin, SoundEvents.FIRECHARGE_USE, 0.5F, 1.2F);
                }
                if (age % pulse == 0) {
                    scorch(level, player, origin, look, range, cosHalf, damage, ignite);
                }
                return ++age >= duration;
            }
        });
        return true;
    }

    private static void scorch(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 look, double range, double cosHalf, double damage, int ignite) {
        AABB box = new AABB(origin, origin.add(look.scale(range))).inflate(range * 0.45);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
            if (target == player) {
                continue;
            }
            Vec3 centre = target.getBoundingBox().getCenter();
            Vec3 toTarget = centre.subtract(origin);
            double distance = toTarget.length();
            if (distance > range || distance < 0.01 || toTarget.scale(1.0 / distance).dot(look) < cosHalf) {
                continue;
            }
            var clip = level.clip(new ClipContext(origin, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (clip.getType() != HitResult.Type.MISS && clip.getLocation().distanceTo(origin) < distance - 0.6) {
                continue;
            }
            Dmg.shoot(level, player, target, damage, true);
            target.igniteForTicks(ignite);
        }
    }
}
