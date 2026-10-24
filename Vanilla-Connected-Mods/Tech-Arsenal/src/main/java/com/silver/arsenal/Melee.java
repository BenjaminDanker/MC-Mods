package com.silver.arsenal;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The right-click abilities of the melee weapons: slam, lunge, lash and rev. */
final class Melee {
    private Melee() {
    }

    /** Pipe Hammer: leap, and hit the ground hard where you land. */
    static final class Slam implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            if (!player.onGround()) {
                player.sendSystemMessage(Component.literal("You need solid ground to launch from."), true);
                return false;
            }
            Params p = c.p();
            double radius = p.num("radius", 3.6);
            double damage = c.dmg("damage", 6.0);
            double knock = p.num("knock", 0.9);
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * 0.45, p.num("leap", 0.95), look.z * 0.45);
            player.hurtMarked = true;
            Fx.sound(c.level(), player.position(), SoundEvents.PISTON_EXTEND, 1.0F, 0.6F);
            ServerLevel level = c.level();
            Engine.add(new Task() {
                private int age;

                @Override
                public boolean tick(MinecraftServer server) {
                    if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                        return true;
                    }
                    player.resetFallDistance();
                    age++;
                    if (age >= 6 && (player.onGround() || age > 80)) {
                        land(level, player, radius, damage, knock);
                        return true;
                    }
                    return false;
                }
            });
            return true;
        }

        private static void land(ServerLevel level, ServerPlayer player, double radius, double damage, double knock) {
            Vec3 at = player.position();
            AABB box = new AABB(at, at).inflate(radius, 2.0, radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
                if (target != player && target.position().distanceTo(at) <= radius) {
                    Dmg.proc(level, player, target, damage);
                    Dmg.shove(target, at, knock, 0.55);
                }
            }
            Fx.burst(level, ParticleTypes.EXPLOSION, at, 3, 1.0, 0.0);
            Fx.burst(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), 40, radius / 2, 0.2);
            Fx.sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, 0.9F);
        }
    }

    /** Thermal Lance: a short dash that hits everything it passes through. */
    static final class Lunge implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            Params p = c.p();
            double distance = p.num("distance", 7);
            double damage = c.dmg("damage", 9);
            double radius = p.num("radius", 1.5);
            int ignite = p.integer("ignite", 60);
            int ticks = 5;
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, Math.max(-0.2, Math.min(0.35, look.y)), look.z).normalize();
            Vec3 step = flat.scale(distance / ticks);
            ServerLevel level = c.level();
            Fx.sound(level, player.position(), SoundEvents.BREEZE_SHOOT, 1.0F, 1.3F);
            Engine.add(new Task() {
                private int age;
                private final Set<LivingEntity> struck = new HashSet<>();

                @Override
                public boolean tick(MinecraftServer server) {
                    if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                        return true;
                    }
                    player.setDeltaMovement(step);
                    player.hurtMarked = true;
                    player.resetFallDistance();
                    Fx.burst(level, ParticleTypes.FLAME, player.position().add(0, 0.9, 0), 6, 0.25, 0.02);
                    AABB box = player.getBoundingBox().inflate(radius);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
                        if (target != player && struck.add(target)) {
                            Dmg.shoot(level, player, target, damage, true);
                            target.igniteForTicks(ignite);
                            Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
                        }
                    }
                    return ++age >= ticks;
                }
            });
            return true;
        }
    }

    /** Monowire Whip: lash the first creature in line and drag it toward you. */
    static final class Lash implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            ServerLevel level = c.level();
            Params p = c.p();
            Vec3 look = player.getLookAngle();
            Shots.Trace trace = Shots.trace(level, player, player.getEyePosition(), look, p.num("range", 7.5), 0, 0.3);
            Vec3 from = Shots.muzzle(player, look);
            Fx.line(level, ParticleTypes.CRIT, from, trace.end(), 0.4);
            Fx.sound(level, player.position(), SoundEvents.CHAIN_HIT, 1.0F, 1.6F);
            if (trace.hits().isEmpty()) {
                return true;
            }
            LivingEntity target = trace.hits().getFirst().entity();
            Dmg.shoot(level, player, target, c.dmg("damage", 4), true);
            Vec3 toPlayer = player.position().subtract(target.position());
            double pull = p.num("pull", 1.0);
            Vec3 haul = toPlayer.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toPlayer.normalize().scale(pull + Math.min(0.9, toPlayer.length() * 0.08));
            target.setDeltaMovement(haul.x, 0.3, haul.z);
            target.hurtMarked = true;
            return true;
        }
    }

    /** Rotary Cutter: for a few seconds every hit is stronger and bleeds (see HitProcs). */
    static final class Rev implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            int ticks = c.p().integer("ticks", 100);
            Engine.startRev(player.getUUID(), Engine.tickCount() + ticks);
            player.sendSystemMessage(Component.literal("Revving."), true);
            Fx.sound(c.level(), player.position(), SoundEvents.GRINDSTONE_USE, 1.2F, 0.7F);
            Fx.burst(c.level(), ParticleTypes.ELECTRIC_SPARK, player.position().add(0, 1.0, 0), 20, 0.4, 0.2);
            return true;
        }
    }
}
