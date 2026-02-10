package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.DragonProvokedAccess;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PhaseManager.class)
public abstract class PhaseManagerMixin {
    private static final String SKY_ISLANDS_MANAGED_TAG = "sky_islands_managed_dragon";
    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseManagerMixin.class);

    @Shadow
    @Final
    private EnderDragonEntity dragon;

    @ModifyVariable(method = "setPhase", at = @At("HEAD"), argsOnly = true)
    private PhaseType<?> skyIslands$blockAggroPhasesUntilProvoked(PhaseType<?> type) {
        PhaseType<?> original = type;

        // Never interfere with the death sequence.
        if (type == PhaseType.DYING) {
            return type;
        }

        // STRAFE_PLAYER often doesn't have a valid target outside The End and can lead to dragons
        // orbiting tightly while logging "Skipping player strafe phase because no player was found".
        if (type == PhaseType.STRAFE_PLAYER) {
            type = PhaseType.TAKEOFF;
        }

        // Passive roaming: managed + unprovoked dragons should not enter landing/sitting/charge/etc.
        // Those phases cause descents and tight circles in the overworld.
        if (this.dragon.getCommandTags().contains(SKY_ISLANDS_MANAGED_TAG)
                && this.dragon instanceof DragonProvokedAccess passiveAccess
                && !passiveAccess.skyIslands$isProvoked()) {
            if (type == PhaseType.LANDING_APPROACH
                    || type == PhaseType.LANDING
                    || type == PhaseType.CHARGING_PLAYER
                    || type == PhaseType.SITTING_ATTACKING
                    || type == PhaseType.SITTING_FLAMING
                    || type == PhaseType.SITTING_SCANNING) {
                type = PhaseType.TAKEOFF;
            }

            // HOLDING_PATTERN is inherently an orbit; for overworld roaming we prefer TAKEOFF.
            if (type == PhaseType.HOLDING_PATTERN) {
                type = PhaseType.TAKEOFF;
            }
        }

        // Managed dragons use custom combat when provoked; landing/sitting phases are undesirable in
        // the overworld and tend to pin the dragon down.
        if (this.dragon.getCommandTags().contains(SKY_ISLANDS_MANAGED_TAG)
                && this.dragon instanceof DragonProvokedAccess access
                && access.skyIslands$isProvoked()) {
            if (type == PhaseType.HOLDING_PATTERN
                    || type == PhaseType.LANDING_APPROACH
                    || type == PhaseType.LANDING
                    || type == PhaseType.SITTING_ATTACKING
                    || type == PhaseType.SITTING_FLAMING
                    || type == PhaseType.SITTING_SCANNING) {
                type = PhaseType.TAKEOFF;
            }
        }

        if (!(this.dragon instanceof DragonProvokedAccess access) || access.skyIslands$isProvoked()) {
            if (LOGGER.isDebugEnabled() && original != type) {
                LOGGER.debug("[Sky-Islands][dragons][phase] redirect uuid={} {} -> {} managed={} provoked={}",
                        this.dragon.getUuidAsString(), original, type,
                        this.dragon.getCommandTags().contains(SKY_ISLANDS_MANAGED_TAG),
                        this.dragon instanceof DragonProvokedAccess a && a.skyIslands$isProvoked());
            }
            return type;
        }

        if (type == PhaseType.CHARGING_PLAYER
                || type == PhaseType.SITTING_ATTACKING
                || type == PhaseType.SITTING_FLAMING) {
            type = PhaseType.TAKEOFF;
        }

        if (LOGGER.isDebugEnabled() && original != type) {
            LOGGER.debug("[Sky-Islands][dragons][phase] redirect uuid={} {} -> {} managed={} provoked={}",
                    this.dragon.getUuidAsString(), original, type,
                    this.dragon.getCommandTags().contains(SKY_ISLANDS_MANAGED_TAG),
                    false);
        }

        return type;
    }
}
