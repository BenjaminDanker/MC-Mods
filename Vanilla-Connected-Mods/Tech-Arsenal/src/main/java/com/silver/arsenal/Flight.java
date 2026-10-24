package com.silver.arsenal;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.Vec3;

/**
 * Follows a thrown projectile (a snowball wearing the round's model, so vanilla clients see a grenade or rocket) and, the moment
 * it is gone, whether from hitting a block or a creature, applies the impact where it stopped.
 */
final class Flight implements Task {
    /** Vanilla's own gravity for a thrown snowball, per tick. */
    private static final double VANILLA_GRAVITY = 0.03;
    private static final int MAX_TICKS = 200;
    private static final boolean DEBUG = Boolean.getBoolean("arsenal.debug");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("tech-arsenal");

    interface Impact {
        void at(ServerLevel level, ServerPlayer owner, Vec3 where);
    }

    private final Snowball projectile;
    private final ServerLevel level;
    private final ServerPlayer owner;
    private final Impact impact;
    private final double gravity;
    private final ParticleOptions trail;
    private int age;
    private Vec3 last;

    Flight(Snowball projectile, ServerLevel level, ServerPlayer owner, double gravity, ParticleOptions trail, Impact impact) {
        this.projectile = projectile;
        this.level = level;
        this.owner = owner;
        this.gravity = gravity;
        this.trail = trail;
        this.impact = impact;
        this.last = projectile.position();
    }

    @Override
    public boolean tick(MinecraftServer server) {
        if (projectile.isRemoved()) {
            if (DEBUG) {
                LOGGER.info("projectile gone at {} after {} ticks (last seen {})", projectile.position(), age, last);
            }
            impact.at(level, owner, projectile.position());
            return true;
        }
        if (++age > MAX_TICKS) {
            Vec3 at = projectile.position();
            projectile.discard();
            impact.at(level, owner, at);
            return true;
        }
        Vec3 velocity = projectile.getDeltaMovement();
        if (gravity != 1.0 && gravity > 0.0) {
            projectile.setDeltaMovement(velocity.add(0, VANILLA_GRAVITY * (1.0 - gravity), 0));
            projectile.hurtMarked = true;
        }
        Vec3 now = projectile.position();
        if (trail != null) {
            Fx.burst(level, trail, now, 1, 0.02, 0.0);
        }
        last = now;
        return false;
    }

    @Override
    public void cancel() {
        projectile.discard();
    }

    static ParticleOptions trail(String name) {
        return switch (name) {
            case "flame" -> ParticleTypes.FLAME;
            case "smoke" -> ParticleTypes.SMOKE;
            case "crit" -> ParticleTypes.CRIT;
            case "spark" -> ParticleTypes.ELECTRIC_SPARK;
            default -> null;
        };
    }

    Vec3 lastSeen() {
        return last;
    }

    static ServerPlayer owner(Snowball ball) {
        return ball.getOwner() instanceof ServerPlayer player ? player : null;
    }
}
