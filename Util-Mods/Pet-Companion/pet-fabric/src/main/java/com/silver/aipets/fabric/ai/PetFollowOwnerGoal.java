package com.silver.aipets.fabric.ai;

import com.silver.aipets.fabric.config.PetPhysicalConfig;
import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;
import java.util.Objects;

/** Owner-only pathing with no teleport and no chunk-loading calls. */
public final class PetFollowOwnerGoal extends Goal {
    private final TameableEntity pet;
    private final PetEntityData data;
    private final PetPhysicalConfig config;
    private final PetStuckDetector stuckDetector;

    private PlayerEntity owner;
    private int refreshCountdown;

    public PetFollowOwnerGoal(TameableEntity pet, PetPhysicalConfig config) {
        this.pet = Objects.requireNonNull(pet, "pet");
        if (!(pet instanceof PetEntityData petData)) {
            throw new IllegalArgumentException("Pet entity is missing identity data");
        }
        this.data = petData;
        this.config = Objects.requireNonNull(config, "config");
        this.stuckDetector = new PetStuckDetector(
                config.stuckTimeoutTicks(),
                config.progressThreshold());
        setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        owner = findLocalOwner();
        return owner != null
                && !data.aipets$isSleeping()
                && pet.squaredDistanceTo(owner)
                > config.followStartDistance() * config.followStartDistance();
    }

    @Override
    public boolean shouldContinue() {
        return owner != null
                && owner.isAlive()
                && !owner.isSpectator()
                && owner.getEntityWorld() == pet.getEntityWorld()
                && !data.aipets$isSleeping()
                && pet.squaredDistanceTo(owner)
                > config.followStopDistance() * config.followStopDistance();
    }

    @Override
    public void start() {
        refreshCountdown = 0;
        stuckDetector.reset(pet.getEntityPos());
    }

    @Override
    public void stop() {
        owner = null;
        pet.getNavigation().stop();
        stuckDetector.reset(pet.getEntityPos());
    }

    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        if (--refreshCountdown > 0) {
            return;
        }
        refreshCountdown = config.pathRefreshTicks();

        if (stuckDetector.sample(pet.getEntityPos(), config.pathRefreshTicks())) {
            pet.getNavigation().stop();
        }

        double squaredDistance = pet.squaredDistanceTo(owner);
        // Use the owner's precise position rather than the entity-target overload.  The
        // latter resolves the target to a navigation node/entity anchor, which makes an
        // otherwise clear diagonal run look like a sequence of block-to-block corrections.
        // The coordinate overload still uses the mob navigation/pathfinder, so collision
        // handling and the existing stuck recovery remain unchanged.
        pet.getNavigation().startMovingTo(
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                config.speedForSquaredDistance(squaredDistance));
    }

    private PlayerEntity findLocalOwner() {
        if (!data.aipets$isPet() || data.aipets$getOwnerUuid() == null) {
            return null;
        }
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }
        PlayerEntity candidate = world.getPlayerAnyDimension(data.aipets$getOwnerUuid());
        if (candidate == null
                || candidate.getEntityWorld() != pet.getEntityWorld()
                || !candidate.isAlive()
                || candidate.isSpectator()) {
            return null;
        }
        return candidate;
    }
}
