package com.silver.atlantis.leviathan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

final class LeviathanCombatRuntime {
    LeviathanCombatSubstate substate = LeviathanCombatSubstate.ACQUIRE;
    UUID targetUuid;
    Vec3 anchorPos;
    Vec3 previousPos;
    Vec3 chargeDirection = new Vec3(1.0, 0.0, 0.0);
    long phaseStartTick;
    long cooldownUntilTick;
    long invalidLineTicks;
    long passId;
    long stalledTicks;
    long missingTargetUntilTick = -1L;
    Vec3 passStartPos;
    Vec3 retreatCenter;
    Vec3 retreatTarget;
    long retreatHoldUntilTick;
    long nextLineOfEngagementCheckTick;
    boolean lastLineOfEngagementValid = true;
    Vec3 lastLineCheckEntityPos;
    Vec3 lastLineCheckTargetPos;
    Vec3 lastAggressiveSteerDirection;
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
