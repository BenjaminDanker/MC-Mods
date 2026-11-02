package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.DragonProvokedAccess;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EnderDragonPhaseManager.class)
public abstract class PhaseManagerMixin {
    private static final String SKY_ISLANDS_MANAGED_TAG = "sky_islands_managed_dragon";
    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseManagerMixin.class);

    @Shadow
    @Final
    private EnderDragon dragon;

    @ModifyVariable(method = "setPhase", at = @At("HEAD"), argsOnly = true)
    private EnderDragonPhase<?> skyIslands$blockAggroPhasesUntilProvoked(EnderDragonPhase<?> type) {
        if (!this.dragon.entityTags().contains(SKY_ISLANDS_MANAGED_TAG)) return type;
        EnderDragonPhase<?> original = type;

        // Never interfere with the death sequence.
        if (type == EnderDragonPhase.DYING) {
            return type;
        }

        // STRAFE_PLAYER often doesn't have a valid target outside The End and can lead to dragons
        // orbiting tightly while logging "Skipping player strafe phase because no player was found".
        if (type == EnderDragonPhase.STRAFE_PLAYER) {
            type = EnderDragonPhase.TAKEOFF;
        }

        // Passive roaming: managed + unprovoked dragons should not enter landing/sitting/charge/etc.
        // Those phases cause descents and tight circles in the overworld.
        if (this.dragon.entityTags().contains(SKY_ISLANDS_MANAGED_TAG)
                && this.dragon instanceof DragonProvokedAccess passiveAccess
                && !passiveAccess.skyIslands$isProvoked()) {
            if (type == EnderDragonPhase.LANDING_APPROACH
                    || type == EnderDragonPhase.LANDING
                    || type == EnderDragonPhase.CHARGING_PLAYER
                    || type == EnderDragonPhase.SITTING_ATTACKING
                    || type == EnderDragonPhase.SITTING_FLAMING
                    || type == EnderDragonPhase.SITTING_SCANNING) {
                type = EnderDragonPhase.TAKEOFF;
            }

            // HOLDING_PATTERN is inherently an orbit; for overworld roaming we prefer TAKEOFF.
            if (type == EnderDragonPhase.HOLDING_PATTERN) {
                type = EnderDragonPhase.TAKEOFF;
            }
        }

        // Managed dragons use custom combat when provoked; landing/sitting phases are undesirable in
        // the overworld and tend to pin the dragon down.
        if (this.dragon.entityTags().contains(SKY_ISLANDS_MANAGED_TAG)
                && this.dragon instanceof DragonProvokedAccess access
                && access.skyIslands$isProvoked()) {
            if (type == EnderDragonPhase.HOLDING_PATTERN
                    || type == EnderDragonPhase.LANDING_APPROACH
                    || type == EnderDragonPhase.LANDING
                    || type == EnderDragonPhase.SITTING_ATTACKING
                    || type == EnderDragonPhase.SITTING_FLAMING
                    || type == EnderDragonPhase.SITTING_SCANNING) {
                type = EnderDragonPhase.TAKEOFF;
            }
        }

        if (!(this.dragon instanceof DragonProvokedAccess access) || access.skyIslands$isProvoked()) {
            if (LOGGER.isDebugEnabled() && original != type) {
                LOGGER.debug("[Sky-Islands][dragons][phase] redirect uuid={} {} -> {} managed={} provoked={}",
                        this.dragon.getStringUUID(), original, type,
                        this.dragon.entityTags().contains(SKY_ISLANDS_MANAGED_TAG),
                        this.dragon instanceof DragonProvokedAccess a && a.skyIslands$isProvoked());
            }
            return type;
        }

        if (type == EnderDragonPhase.CHARGING_PLAYER
                || type == EnderDragonPhase.SITTING_ATTACKING
                || type == EnderDragonPhase.SITTING_FLAMING) {
            type = EnderDragonPhase.TAKEOFF;
        }

        if (LOGGER.isDebugEnabled() && original != type) {
            LOGGER.debug("[Sky-Islands][dragons][phase] redirect uuid={} {} -> {} managed={} provoked={}",
                    this.dragon.getStringUUID(), original, type,
                    this.dragon.entityTags().contains(SKY_ISLANDS_MANAGED_TAG),
                    false);
        }

        return type;
    }
}
