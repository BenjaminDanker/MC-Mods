package com.silver.atlantis.leviathan;

import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class LeviathanCombatRuntime {
    LeviathanCombatSubstate substate = LeviathanCombatSubstate.ACQUIRE;
    UUID targetUuid;
    Vec3d anchorPos;
    Vec3d previousPos;
    Vec3d chargeDirection = new Vec3d(1.0, 0.0, 0.0);
    long phaseStartTick;
    long cooldownUntilTick;
    long invalidLineTicks;
    long passId;
    long stalledTicks;
    long missingTargetUntilTick = -1L;
    Vec3d passStartPos;
    Vec3d retreatCenter;
    Vec3d retreatTarget;
    long retreatHoldUntilTick;
    long nextLineOfEngagementCheckTick;
    boolean lastLineOfEngagementValid = true;
    Vec3d lastLineCheckEntityPos;
    Vec3d lastLineCheckTargetPos;
    Vec3d lastAggressiveSteerDirection;
    long lastAggressiveSteerTick = Long.MIN_VALUE;
    final Map<UUID, Long> lastHitTickByPlayer = new HashMap<>();
    final Map<UUID, Long> lastHitPassByPlayer = new HashMap<>();

    void resetToPassive() {
        substate = LeviathanCombatSubstate.ACQUIRE;
        targetUuid = null;
        anchorPos = null;
        cooldownUntilTick = 0L;
        invalidLineTicks = 0L;
        stalledTicks = 0L;
        missingTargetUntilTick = -1L;
        passStartPos = null;
        retreatCenter = null;
        retreatTarget = null;
        retreatHoldUntilTick = 0L;
        nextLineOfEngagementCheckTick = 0L;
        lastLineOfEngagementValid = true;
        lastLineCheckEntityPos = null;
        lastLineCheckTargetPos = null;
        lastAggressiveSteerDirection = null;
        lastAggressiveSteerTick = Long.MIN_VALUE;
    }
}
