package com.silver.aipets.fabric.ai;

import com.silver.aipets.fabric.config.PetPhysicalConfig;
import com.silver.aipets.fabric.entity.PetEntityData;
import java.util.EnumSet;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Owner-only pathing with no teleport and no chunk-loading calls. */
public final class PetFollowOwnerGoal extends Goal {
    private final TamableAnimal pet;
    private final PetEntityData data;
    private final PetPhysicalConfig config;
    private final PetStuckDetector stuckDetector;

    private Player owner;
    private int refreshCountdown;

    public PetFollowOwnerGoal(TamableAnimal pet, PetPhysicalConfig config) {
        this.pet = Objects.requireNonNull(pet, "pet");
        if (!(pet instanceof PetEntityData petData)) {
            throw new IllegalArgumentException("Pet entity is missing identity data");
        }
        this.data = petData;
        this.config = Objects.requireNonNull(config, "config");
        this.stuckDetector = new PetStuckDetector(
                config.stuckTimeoutTicks(),
                config.progressThreshold());
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        owner = findLocalOwner();
        return owner != null
                && !data.aipets$isSleeping()
                && pet.distanceToSqr(owner)
                > config.followStartDistance() * config.followStartDistance();
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null
                && owner.isAlive()
                && !owner.isSpectator()
                && owner.level() == pet.level()
                && !data.aipets$isSleeping()
                && pet.distanceToSqr(owner)
                > config.followStopDistance() * config.followStopDistance();
    }

    @Override
    public void start() {
        refreshCountdown = 0;
        stuckDetector.reset(pet.position());
    }

    @Override
    public void stop() {
        owner = null;
        pet.getNavigation().stop();
        stuckDetector.reset(pet.position());
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

        if (stuckDetector.sample(pet.position(), config.pathRefreshTicks())) {
            pet.getNavigation().stop();
        }

        double squaredDistance = pet.distanceToSqr(owner);
        // Use the owner's precise position rather than the entity-target overload.  The
        // latter resolves the target to a navigation node/entity anchor, which makes an
        // otherwise clear diagonal run look like a sequence of block-to-block corrections.
        // The coordinate overload still uses the mob navigation/pathfinder, so collision
        // handling and the existing stuck recovery remain unchanged.
        pet.getNavigation().moveTo(
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                config.speedForSquaredDistance(squaredDistance));
    }

    private Player findLocalOwner() {
        if (!data.aipets$isPet() || data.aipets$getOwnerUuid() == null) {
            return null;
        }
        if (!(pet.level() instanceof ServerLevel world)) {
            return null;
        }
        Player candidate = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
        if (candidate == null
                || candidate.level() != pet.level()
                || !candidate.isAlive()
                || candidate.isSpectator()) {
            return null;
        }
        return candidate;
    }
}
