package com.silver.arsenal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Grapple gun, stim pack, barrier emitter, proximity mine and sentry turret. */
final class Gadgets {
    private static final Map<UUID, List<Task>> DEPLOYED = new HashMap<>();

    private Gadgets() {
    }

    /** Keeps at most {@code max} deployments per owner and kind: placing another removes the oldest. */
    private static void limit(UUID owner, String kind, int max, Task fresh) {
        // Sweep every owner, so players who stopped placing gadgets don't keep finished tasks forever.
        DEPLOYED.values().forEach(tasks -> tasks.removeIf(task -> task instanceof Deployed d && d.finished()));
        DEPLOYED.values().removeIf(List::isEmpty);
        List<Task> mine = DEPLOYED.computeIfAbsent(owner, id -> new ArrayList<>());
        long same = mine.stream().filter(t -> t instanceof Deployed d && d.kind().equals(kind)).count();
        while (same >= max) {
            for (Task task : mine) {
                if (task instanceof Deployed d && d.kind().equals(kind)) {
                    d.expire();
                    mine.remove(task);
                    break;
                }
            }
            same--;
        }
        mine.add(fresh);
    }

    private interface Deployed {
        String kind();

        boolean finished();

        void expire();
    }

    // -------------------------------------------------------------------- grapple

    static final class Grapple implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            ServerLevel level = c.level();
            Params p = c.p();
            Vec3 look = player.getLookAngle();
            Shots.Trace trace = Shots.trace(level, player, player.getEyePosition(), look, p.num("range", 42), -1, 0);
            if (!trace.hitBlock()) {
                player.sendSystemMessage(Component.literal("Nothing in range to grab."), true);
                return false;
            }
            Vec3 anchor = trace.end().subtract(look.scale(0.3));
            double speed = p.num("speed", 1.1);
            int maxTicks = p.integer("max_ticks", 30);
            Fx.sound(level, player.position(), SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.6F);
            Engine.add(new Task() {
                private int age;

                @Override
                public boolean tick(MinecraftServer server) {
                    if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                        return true;
                    }
                    Vec3 toAnchor = anchor.subtract(player.getEyePosition());
                    if (age > 0 && (toAnchor.length() < 2.2 || age >= maxTicks || player.isShiftKeyDown())) {
                        Fx.applyEffect(player, "slow_falling", 60, 0);
                        player.resetFallDistance();
                        return true;
                    }
                    player.setDeltaMovement(toAnchor.normalize().scale(speed));
                    player.hurtMarked = true;
                    player.resetFallDistance();
                    if (age % 2 == 0) {
                        Fx.line(level, ParticleTypes.CRIT, Shots.muzzle(player, player.getLookAngle()).add(0, -0.1, 0), anchor, 0.9);
                    }
                    age++;
                    return false;
                }
            });
            return true;
        }
    }

    // -------------------------------------------------------------------- stim and barrier

    static final class Stim implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            Params p = c.p();
            if (player.getHealth() >= player.getMaxHealth()) {
                player.sendSystemMessage(Component.literal("You are already at full health."), true);
                return false;
            }
            player.heal((float) p.num("heal", 6));
            Fx.applyEffect(player, "regeneration", p.integer("regen_ticks", 100), p.integer("regen_amp", 1));
            Fx.burst(c.level(), ParticleTypes.HEART, player.position().add(0, 1.4, 0), 6, 0.3, 0.0);
            Fx.sound(c.level(), player.position(), SoundEvents.BOTTLE_FILL, 1.0F, 1.3F);
            return true;
        }
    }

    static final class Barrier implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            Params p = c.p();
            Fx.applyEffect(player, "absorption", p.integer("absorption_ticks", 240), p.integer("absorption_amp", 1));
            Fx.applyEffect(player, "resistance", p.integer("resistance_ticks", 120), 0);
            ServerLevel level = c.level();
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16;
                Vec3 at = player.position().add(Math.cos(angle) * 1.1, 0.1 + (i % 4) * 0.5, Math.sin(angle) * 1.1);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0.0);
            }
            Fx.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F);
            return true;
        }
    }

    // -------------------------------------------------------------------- mine

    static final class Mine implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            ServerLevel level = c.level();
            Params p = c.p();
            Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
            Vec3 start = player.position().add(forward.scale(1.2)).add(0, 1.0, 0);
            var ground = level.clip(new ClipContext(start, start.add(0, -3.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (ground.getType() == HitResult.Type.MISS) {
                player.sendSystemMessage(Component.literal("There is no ground to set it on."), true);
                return false;
            }
            Vec3 at = ground.getLocation().add(0, 0.04, 0);
            if (!Displays.available()) {
                return false;
            }
            Display.ItemDisplay display = Displays.spawn(level, at.add(0, 0.5, 0), player.getYRot(), "proximity_mine_placed", 1.0F, new Quaternionf());
            var blast = p.child("blast");
            double blastDamage = blast.num("damage", 9) * (c.tempered() ? Tuning.temperedDamage : 1.0);
            MineTask mine = new MineTask(level, player, display, at, p.integer("arm_ticks", 30), p.num("trigger_radius", 2.2),
                p.integer("life", 3600), blast.num("radius", 3.4), blastDamage, blast.num("knock", 0.9));
            limit(player.getUUID(), "mine", p.integer("max", 3), mine);
            Engine.add(mine);
            Fx.sound(level, at, SoundEvents.TRIPWIRE_ATTACH, 1.0F, 0.8F);
            return true;
        }
    }

    private static final class MineTask implements Task, Deployed {
        private final ServerLevel level;
        private final ServerPlayer owner;
        private final Display.ItemDisplay display;
        private final Vec3 at;
        private final int armTicks;
        private final double triggerRadius;
        private final int life;
        private final double radius;
        private final double damage;
        private final double knock;
        private int age;
        private boolean done;

        MineTask(ServerLevel level, ServerPlayer owner, Display.ItemDisplay display, Vec3 at, int armTicks, double triggerRadius,
                int life, double radius, double damage, double knock) {
            this.level = level;
            this.owner = owner;
            this.display = display;
            this.at = at;
            this.armTicks = armTicks;
            this.triggerRadius = triggerRadius;
            this.life = life;
            this.radius = radius;
            this.damage = damage;
            this.knock = knock;
        }

        @Override
        public String kind() {
            return "mine";
        }

        @Override
        public boolean finished() {
            return done;
        }

        @Override
        public void expire() {
            done = true;
            display.discard();
        }

        @Override
        public boolean tick(MinecraftServer server) {
            if (done || display.isRemoved()) {
                done = true;
                return true;
            }
            age++;
            if (age > life) {
                expire();
                return true;
            }
            if (age == armTicks) {
                Fx.sound(level, at, SoundEvents.TRIPWIRE_CLICK_ON, 0.8F, 1.6F);
            }
            if (age < armTicks) {
                return false;
            }
            if (age % 20 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.1, at.z, 1, 0.05, 0.02, 0.05, 0.0);
            }
            AABB box = new AABB(at, at).inflate(triggerRadius, 1.5, triggerRadius);
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
                if (near != owner && near.position().distanceTo(at) <= triggerRadius) {
                    Dmg.blast(level, owner, at, radius, damage, knock);
                    Fx.bang(level, at, radius);
                    expire();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void cancel() {
            display.discard();
        }
    }

    // -------------------------------------------------------------------- turret

    static final class Turret implements Action {
        @Override
        public boolean use(Ctx c) {
            ServerPlayer player = c.player();
            ServerLevel level = c.level();
            Params p = c.p();
            Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
            Vec3 start = player.position().add(forward.scale(1.4)).add(0, 1.0, 0);
            var ground = level.clip(new ClipContext(start, start.add(0, -3.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (ground.getType() == HitResult.Type.MISS || !Displays.available()) {
                player.sendSystemMessage(Component.literal("There is no ground to set it on."), true);
                return false;
            }
            Vec3 at = ground.getLocation();
            Display.ItemDisplay display = Displays.spawn(level, at.add(0, 0.5, 0), player.getYRot(), "sentry_turret_deployed", 1.0F, new Quaternionf());
            TurretTask turret = new TurretTask(level, player, display, at.add(0, 0.9, 0), p.integer("ticks", 500), p.num("range", 14),
                p.integer("every", 8), c.dmg("damage", 1.6));
            limit(player.getUUID(), "turret", p.integer("max", 1), turret);
            Engine.add(turret);
            Fx.sound(level, at, SoundEvents.ANVIL_PLACE, 0.8F, 1.4F);
            Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.5, 0), 25, 0.4, 0.2);
            return true;
        }
    }

    private static final class TurretTask implements Task, Deployed {
        private final ServerLevel level;
        private final ServerPlayer owner;
        private final Display.ItemDisplay display;
        private final Vec3 muzzle;
        private final int life;
        private final double range;
        private final int every;
        private final double damage;
        private int age;
        private boolean done;

        TurretTask(ServerLevel level, ServerPlayer owner, Display.ItemDisplay display, Vec3 muzzle, int life, double range, int every, double damage) {
            this.level = level;
            this.owner = owner;
            this.display = display;
            this.muzzle = muzzle;
            this.life = life;
            this.range = range;
            this.every = Math.max(1, every);
            this.damage = damage;
        }

        @Override
        public String kind() {
            return "turret";
        }

        @Override
        public boolean finished() {
            return done;
        }

        @Override
        public void expire() {
            done = true;
            Fx.burst(level, ParticleTypes.POOF, muzzle, 20, 0.3, 0.05);
            display.discard();
        }

        @Override
        public boolean tick(MinecraftServer server) {
            if (done || display.isRemoved()) {
                done = true;
                return true;
            }
            if (++age > life || !owner.isAlive() && age > 40) {
                expire();
                return true;
            }
            if (age % every != 0) {
                return false;
            }
            LivingEntity target = pick();
            if (target == null) {
                return false;
            }
            Vec3 aim = target.getBoundingBox().getCenter();
            Vec3 toTarget = aim.subtract(muzzle);
            float yaw = (float) (Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z)));
            display.snapTo(display.getX(), display.getY(), display.getZ(), yaw, 0.0F);
            Fx.tracer(level, muzzle, aim, 0xFFE066, 0.7F, 0.8);
            Dmg.shoot(level, owner, target, damage, true);
            Fx.sound(level, muzzle, SoundEvents.CROSSBOW_SHOOT, 0.5F, 1.9F);
            return false;
        }

        /** The nearest hostile creature, or a player other than the owner, that the turret can see. */
        private LivingEntity pick() {
            AABB box = new AABB(muzzle, muzzle).inflate(range);
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, Dmg::living)) {
                boolean hostile = candidate instanceof Enemy;
                boolean rival = candidate instanceof ServerPlayer player && player != owner && !player.isCreative();
                if (!hostile && !rival) {
                    continue;
                }
                Vec3 centre = candidate.getBoundingBox().getCenter();
                double distance = centre.distanceTo(muzzle);
                if (distance > range || distance >= bestDistance) {
                    continue;
                }
                var clip = level.clip(new ClipContext(muzzle, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                if (clip.getType() != HitResult.Type.MISS && clip.getLocation().distanceTo(muzzle) < distance - 0.6) {
                    continue;
                }
                best = candidate;
                bestDistance = distance;
            }
            return best;
        }

        @Override
        public void cancel() {
            display.discard();
        }
    }
}
