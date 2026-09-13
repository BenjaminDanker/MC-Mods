package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class LeviathanRuntimeService {

    private static final double PREDATOR_RETREAT_RADIUS_BLOCKS = 130.0d;
    private static final double PREDATOR_CIRCLE_RADIUS_BLOCKS = 130.0d;
    private static final double PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS = 90.0d;
    private static final double PREDATOR_RETREAT_REACHED_EPSILON_BLOCKS = 14.0d;
    private static final long LINE_OF_ENGAGEMENT_CACHE_TICKS = 2L;
    private static final double LINE_OF_ENGAGEMENT_ENTITY_MOVE_CACHE_SQ = 1.0d;
    private static final double LINE_OF_ENGAGEMENT_TARGET_MOVE_CACHE_SQ = 1.0d;
    private static final long AGGRESSIVE_STEER_CACHE_TICKS = 2L;

    private LeviathanRuntimeService() {
    }

    static boolean updateCombat(ServerLevel world,
                                Entity entity,
                                LeviathanCombatRuntime runtime,
                                LeviathansConfig config,
                                long serverTicks,
                                VirtualLeviathanStore virtualStore) {
        if (!config.combatEnabled) {
            runtime.resetToPassive();
            runtime.previousPos = posOf(entity);
            return false;
        }

        if (runtime.targetUuid == null) {
            ServerPlayer initialTarget = findNearestTarget(world, posOf(entity), config.engageRadiusBlocks, config.engageVerticalRadiusBlocks);
            if (initialTarget == null) {
                runtime.previousPos = posOf(entity);
                return false;
            }
            runtime.targetUuid = initialTarget.getUUID();
            runtime.anchorPos = posOf(initialTarget);
            runtime.phaseStartTick = serverTicks;
            runtime.substate = LeviathanCombatSubstate.ACQUIRE;
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement start leviathan={} target={}", shortId(entity.getUUID()), shortId(initialTarget.getUUID()));
        }

        ServerPlayer target = world.getServer() == null ? null : world.getServer().getPlayerList().getPlayer(runtime.targetUuid);
        Vec3 entityPos = posOf(entity);
        Vec3 previousPos = runtime.previousPos == null ? entityPos : runtime.previousPos;

        if (target == null || !target.isAlive() || target.isSpectator()) {
            if (runtime.anchorPos == null) {
                runtime.anchorPos = entityPos;
            }
            if (runtime.missingTargetUntilTick < 0L) {
                runtime.missingTargetUntilTick = serverTicks + config.disconnectLingerTicks;
                runtime.phaseStartTick = serverTicks;
            }
            if (serverTicks > runtime.missingTargetUntilTick) {
                AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (target missing timeout) leviathan={} target={}",
                    shortId(entity.getUUID()),
                    runtime.targetUuid == null ? "<none>" : shortId(runtime.targetUuid));
                runtime.resetToPassive();
                runtime.previousPos = entityPos;
                return false;
            }

            circleAroundAnchor(world, entity, runtime.anchorPos, config.passOvershootBlocks, config.virtualSpeedBlocksPerTick, serverTicks, config);
            runtime.substate = LeviathanCombatSubstate.TURN_BACK;
            runtime.previousPos = entityPos;
            return true;
        }

        Vec3 targetPos = posOf(target);
        runtime.anchorPos = targetPos;
        runtime.missingTargetUntilTick = -1L;

        if (runtime.substate == LeviathanCombatSubstate.REACQUIRE
            && entityPos.distanceTo(targetPos) <= PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS) {
            if (serverTicks >= runtime.cooldownUntilTick) {
                AtlantisMod.LOGGER.info("[Atlantis][leviathan] predator retreat canceled by close target distance={} <= {} leviathan={} target={}",
                    round1(entityPos.distanceTo(targetPos)),
                    round1(PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS),
                    shortId(entity.getUUID()),
                    shortId(target.getUUID()));
                beginCharge(runtime, entityPos, target, serverTicks, config);
            } else {
                runtime.substate = LeviathanCombatSubstate.ACQUIRE;
                runtime.phaseStartTick = serverTicks;
            }
        }

        if (!isWithinEngagementRange(entityPos, targetPos, config.disengageRadiusBlocks, config.disengageVerticalRadiusBlocks)) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (out of range) leviathan={} target={} distance={} disengageRadius={}",
                shortId(entity.getUUID()),
                shortId(target.getUUID()),
                round1(entityPos.distanceTo(targetPos)),
                config.disengageRadiusBlocks);
            runtime.resetToPassive();
            runtime.previousPos = entityPos;
            return false;
        }

        if (!hasLineOfEngagementCached(world, entity, target, runtime, entityPos, targetPos, serverTicks)) {
            runtime.invalidLineTicks++;
        } else {
            runtime.invalidLineTicks = 0L;
        }
        if (runtime.invalidLineTicks > config.lineOfEngagementTimeoutTicks) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (line timeout) leviathan={} target={} invalidTicks={} timeout={}",
                shortId(entity.getUUID()),
                shortId(target.getUUID()),
                runtime.invalidLineTicks,
                config.lineOfEngagementTimeoutTicks);
            runtime.resetToPassive();
            runtime.previousPos = entityPos;
            return false;
        }

        switch (runtime.substate) {
            case ACQUIRE -> {
                if (serverTicks >= runtime.cooldownUntilTick) {
                    beginCharge(runtime, entityPos, target, serverTicks, config);
                } else {
                    runtime.substate = LeviathanCombatSubstate.TURN_BACK;
                    runtime.phaseStartTick = serverTicks;
                    circleAroundAnchor(world, entity, targetPos, Math.max(8.0d, config.passOvershootBlocks), config.virtualSpeedBlocksPerTick, serverTicks, config);
                }
            }
            case CHARGE -> {
                Vec3 guidedDirection = refineChargeDirection(runtime, entityPos, target, 6.0d, 0.045d, config);
                runtime.chargeDirection = guidedDirection;
                detectChargeHit(world, entity, target, runtime, previousPos, entityPos, config, serverTicks, virtualStore);
                Vec3 steered = chooseSubmergedDirectionWithCache(world, entityPos, guidedDirection, config, runtime, serverTicks);
                entity.setDeltaMovement(steered.scale(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if (detectChargeStall(entity, runtime, previousPos, entityPos, config, serverTicks)) {
                    break;
                }

                if ((serverTicks - runtime.phaseStartTick) >= config.chargeDurationTicks) {
                    runtime.substate = LeviathanCombatSubstate.PASS_THROUGH;
                    runtime.phaseStartTick = serverTicks;
                    runtime.cooldownUntilTick = serverTicks + config.chargeCooldownTicks;
                    runtime.stalledTicks = 0L;
                    runtime.passStartPos = entityPos;
                }
            }
            case PASS_THROUGH -> {
                detectChargeHit(world, entity, target, runtime, previousPos, entityPos, config, serverTicks, virtualStore);
                Vec3 steered = chooseSubmergedDirectionWithCache(world, entityPos, runtime.chargeDirection, config, runtime, serverTicks);
                entity.setDeltaMovement(steered.scale(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if (detectChargeStall(entity, runtime, previousPos, entityPos, config, serverTicks)) {
                    break;
                }

                if (runtime.passStartPos == null) {
                    runtime.passStartPos = previousPos == null ? entityPos : previousPos;
                }

                if (entityPos.distanceTo(runtime.passStartPos) >= PREDATOR_RETREAT_RADIUS_BLOCKS) {
                    startPredatorRetreat(runtime, entityPos, targetPos, target.getUUID(), serverTicks);
                }
            }
            case TURN_BACK -> predatorRetreatStep(world, entity, runtime, targetPos, target.getUUID(), serverTicks, config);
            case REACQUIRE -> {
                if (serverTicks >= runtime.cooldownUntilTick) {
                    runtime.substate = LeviathanCombatSubstate.ACQUIRE;
                    runtime.phaseStartTick = serverTicks;
                } else {
                    predatorCircleStep(world, entity, runtime, serverTicks, config);
                }
            }
        }

        runtime.previousPos = entityPos;
        return true;
    }

    static void applyLoadedPassiveMovement(ServerLevel world,
                                           Entity entity,
                                           VirtualLeviathanStore.VirtualLeviathanState state,
                                           LeviathansConfig config) {
        Vec3 heading = normalizeXZ(state.headingX(), state.headingZ());
        Vec3 steered = chooseSubmergedDirection(world, posOf(entity), heading, false, config);
        double speed = Math.max(0.01d, config.virtualSpeedBlocksPerTick);

        entity.setDeltaMovement(steered.x * speed, steered.y * speed, steered.z * speed);
        faceAlong(entity, steered);
    }

    private static void beginCharge(LeviathanCombatRuntime runtime,
                                    Vec3 entityPos,
                                    ServerPlayer target,
                                    long serverTicks,
                                    LeviathansConfig config) {
        Vec3 intercept = posOf(target).add(target.getDeltaMovement().scale(Math.min(10.0d, config.chargeDurationTicks * 0.25d)));
        Vec3 raw = intercept.subtract(entityPos);
        double len = raw.length();
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Charge direction invalid (zero length) for target=" + target.getStringUUID());
        }

        runtime.chargeDirection = raw.scale(1.0d / len);
        runtime.substate = LeviathanCombatSubstate.CHARGE;
        runtime.phaseStartTick = serverTicks;
        runtime.stalledTicks = 0L;
        runtime.lastAggressiveSteerDirection = null;
        runtime.lastAggressiveSteerTick = Long.MIN_VALUE;
        runtime.passId++;
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] charge begin target={} passId={} direction=({}, {}, {})",
            shortId(target.getUUID()),
            runtime.passId,
            round2(runtime.chargeDirection.x),
            round2(runtime.chargeDirection.y),
            round2(runtime.chargeDirection.z));
    }

    private static Vec3 refineChargeDirection(LeviathanCombatRuntime runtime,
                                               Vec3 entityPos,
                                               ServerPlayer target,
                                               double maxHorizontalTurnDegrees,
                                               double maxVerticalStep,
                                               LeviathansConfig config) {
        Vec3 current = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double lookaheadTicks = clamp(config.chargeDurationTicks * 0.15d, 2.0d, 7.0d);
        Vec3 intercept = posOf(target).add(target.getDeltaMovement().scale(lookaheadTicks));
        Vec3 toIntercept = intercept.subtract(entityPos);
        double len = toIntercept.length();
        if (len < 1.0e-6d) {
            return current;
        }

        Vec3 desired = toIntercept.scale(1.0d / len);
        double maxTurnRadians = Math.toRadians(Math.max(0.5d, maxHorizontalTurnDegrees));
        return steerChargeDirection3d(current, desired, maxTurnRadians, Math.max(0.01d, maxVerticalStep));
    }

    private static Vec3 steerChargeDirection3d(Vec3 current,
                                                Vec3 desired,
                                                double maxHorizontalTurnRadians,
                                                double maxVerticalStep) {
        Vec3 currentNorm = normalizeVectorStrict(current);
        Vec3 desiredNorm = normalizeVectorStrict(desired);

        Vec3 currentHorizontal = new Vec3(currentNorm.x, 0.0d, currentNorm.z);
        Vec3 desiredHorizontal = new Vec3(desiredNorm.x, 0.0d, desiredNorm.z);

        Vec3 steeredHorizontal;
        double currentHorizontalSq = currentHorizontal.lengthSqr();
        double desiredHorizontalSq = desiredHorizontal.lengthSqr();
        if (currentHorizontalSq < 1.0e-6d && desiredHorizontalSq < 1.0e-6d) {
            steeredHorizontal = new Vec3(1.0d, 0.0d, 0.0d);
        } else if (currentHorizontalSq < 1.0e-6d) {
            steeredHorizontal = normalizeXZ(desiredHorizontal.x, desiredHorizontal.z);
        } else if (desiredHorizontalSq < 1.0e-6d) {
            steeredHorizontal = normalizeXZ(currentHorizontal.x, currentHorizontal.z);
        } else {
            steeredHorizontal = turnLimited(
                normalizeXZ(currentHorizontal.x, currentHorizontal.z),
                normalizeXZ(desiredHorizontal.x, desiredHorizontal.z),
                maxHorizontalTurnRadians);
        }

        double steeredY = currentNorm.y + clamp(desiredNorm.y - currentNorm.y, -maxVerticalStep, maxVerticalStep);
        return normalizeVectorStrict(new Vec3(steeredHorizontal.x, steeredY, steeredHorizontal.z));
    }

    private static boolean detectChargeStall(Entity entity,
                                             LeviathanCombatRuntime runtime,
                                             Vec3 previousPos,
                                             Vec3 currentPos,
                                             LeviathansConfig config,
                                             long serverTicks) {
        if (previousPos == null || currentPos == null) {
            runtime.stalledTicks = 0L;
            return false;
        }
        if (runtime.substate != LeviathanCombatSubstate.CHARGE && runtime.substate != LeviathanCombatSubstate.PASS_THROUGH) {
            runtime.stalledTicks = 0L;
            return false;
        }

        double movedSq = currentPos.distanceToSqr(previousPos);
        if (movedSq < 0.0025d) {
            runtime.stalledTicks++;
        } else {
            runtime.stalledTicks = 0L;
            return false;
        }

        if (runtime.stalledTicks < 12L) {
            return false;
        }

        AtlantisMod.LOGGER.warn("[Atlantis][leviathan] charge stalled; forcing turn-back leviathan={} substate={} stalledTicks={} movedSq={}",
            shortId(entity.getUUID()),
            runtime.substate,
            runtime.stalledTicks,
            round2(movedSq));
        runtime.substate = LeviathanCombatSubstate.TURN_BACK;
        runtime.phaseStartTick = serverTicks;
        runtime.cooldownUntilTick = serverTicks + config.chargeCooldownTicks;
        runtime.stalledTicks = 0L;
        runtime.passStartPos = null;
        runtime.retreatCenter = currentPos;
        runtime.retreatTarget = null;
        runtime.retreatHoldUntilTick = 0L;
        return true;
    }

    private static void startPredatorRetreat(LeviathanCombatRuntime runtime,
                                             Vec3 entityPos,
                                             Vec3 targetPos,
                                             UUID targetId,
                                             long serverTicks) {
        runtime.substate = LeviathanCombatSubstate.TURN_BACK;
        runtime.phaseStartTick = serverTicks;
        runtime.stalledTicks = 0L;
        runtime.passStartPos = null;
        runtime.retreatCenter = entityPos;
        runtime.retreatTarget = selectRetreatTarget(entityPos, targetPos, runtime.passId, targetId);
        runtime.retreatHoldUntilTick = 0L;
    }

    private static Vec3 selectRetreatTarget(Vec3 entityPos, Vec3 targetPos, long passId, UUID targetId) {
        Vec3 fromTarget = new Vec3(entityPos.x - targetPos.x, 0.0d, entityPos.z - targetPos.z);
        Vec3 base;
        try {
            base = normalizeXZ(fromTarget.x, fromTarget.z);
        } catch (IllegalStateException ignored) {
            base = new Vec3(1.0d, 0.0d, 0.0d);
        }

        long seed = passId ^ targetId.getLeastSignificantBits() ^ targetId.getMostSignificantBits();
        Random random = new Random(seed);
        double angleJitter = (random.nextDouble() * Math.PI * 1.2d) - (Math.PI * 0.6d);
        double baseAngle = Math.atan2(base.z, base.x) + angleJitter;

        double tx = targetPos.x + Math.cos(baseAngle) * PREDATOR_RETREAT_RADIUS_BLOCKS;
        double tz = targetPos.z + Math.sin(baseAngle) * PREDATOR_RETREAT_RADIUS_BLOCKS;
        return new Vec3(tx, targetPos.y, tz);
    }

    private static void predatorRetreatStep(ServerLevel world,
                                            Entity entity,
                                            LeviathanCombatRuntime runtime,
                                            Vec3 targetPos,
                                            UUID targetId,
                                            long serverTicks,
                                            LeviathansConfig config) {
        Vec3 entityPos = posOf(entity);

        if (runtime.retreatTarget == null) {
            runtime.retreatTarget = selectRetreatTarget(entityPos, targetPos, runtime.passId, targetId);
        }
        if (runtime.retreatCenter == null) {
            runtime.retreatCenter = entityPos;
        }

        Vec3 retreatTarget = new Vec3(runtime.retreatTarget.x, entityPos.y, runtime.retreatTarget.z);
        Vec3 toRetreat = retreatTarget.subtract(entityPos);
        double retreatDistance = toRetreat.length();

        if (retreatDistance <= PREDATOR_RETREAT_REACHED_EPSILON_BLOCKS) {
            runtime.substate = LeviathanCombatSubstate.REACQUIRE;
            runtime.phaseStartTick = serverTicks;
            long seed = runtime.passId ^ targetId.getMostSignificantBits() ^ targetId.getLeastSignificantBits() ^ Double.doubleToLongBits(entityPos.x + entityPos.z);
            Random random = new Random(seed);
            int minHold = 30;
            int maxHold = 100;
            runtime.retreatHoldUntilTick = serverTicks + minHold + random.nextInt((maxHold - minHold) + 1);
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] predator retreat reached; entering circling phase holdUntilTick={} id={}",
                runtime.retreatHoldUntilTick,
                shortId(entity.getUUID()));
            return;
        }

        Vec3 centerToEntity = new Vec3(entityPos.x - runtime.retreatCenter.x, 0.0d, entityPos.z - runtime.retreatCenter.z);
        Vec3 tangent;
        try {
            Vec3 radial = normalizeXZ(centerToEntity.x, centerToEntity.z);
            tangent = new Vec3(-radial.z, 0.0d, radial.x);
        } catch (IllegalStateException ignored) {
            tangent = new Vec3(0.0d, 0.0d, 1.0d);
        }

        Vec3 toward;
        try {
            toward = normalizeXZ(toRetreat.x, toRetreat.z);
        } catch (IllegalStateException ignored) {
            toward = tangent;
        }
        Vec3 desired = normalizeVectorStrict(toward.scale(0.75d).add(tangent.scale(0.45d)));

        Vec3 steered = chooseSubmergedDirectionWithCache(world, entityPos, desired, config, runtime, serverTicks);
        double recuperateSpeed = Math.max(0.06d, Math.min(config.virtualSpeedBlocksPerTick, config.chargeSpeedBlocksPerTick * 0.5d));
        entity.setDeltaMovement(steered.scale(recuperateSpeed));
        faceAlong(entity, steered);
    }

    private static void predatorCircleStep(ServerLevel world,
                                           Entity entity,
                                           LeviathanCombatRuntime runtime,
                                           long serverTicks,
                                           LeviathansConfig config) {
        Vec3 entityPos = posOf(entity);
        Vec3 center = runtime.retreatTarget == null ? entityPos : runtime.retreatTarget;

        double baseRadius = PREDATOR_CIRCLE_RADIUS_BLOCKS;
        double angleSpeed = 0.05d;
        double elapsed = Math.max(0.0d, (double) (serverTicks - runtime.phaseStartTick));
        double angle = elapsed * angleSpeed;
        if ((runtime.passId & 1L) == 0L) {
            angle *= -1.0d;
        }

        Vec3 orbitPoint = new Vec3(
            center.x + Math.cos(angle) * baseRadius,
            entityPos.y,
            center.z + Math.sin(angle) * baseRadius
        );
        Vec3 desired = orbitPoint.subtract(entityPos);
        if (desired.length() < 1.0e-6d) {
            desired = new Vec3(1.0d, 0.0d, 0.0d);
        }

        Vec3 steered = chooseSubmergedDirectionWithCache(world, entityPos, desired.normalize(), config, runtime, serverTicks);
        double recuperateSpeed = Math.max(0.06d, Math.min(config.virtualSpeedBlocksPerTick, config.chargeSpeedBlocksPerTick * 0.45d));
        entity.setDeltaMovement(steered.scale(recuperateSpeed));
        faceAlong(entity, steered);
    }

    private static void detectChargeHit(ServerLevel world,
                                        Entity leviathan,
                                        ServerPlayer target,
                                        LeviathanCombatRuntime runtime,
                                        Vec3 segmentStart,
                                        Vec3 segmentEnd,
                                        LeviathansConfig config,
                                        long serverTicks,
                                        VirtualLeviathanStore virtualStore) {
        if (segmentStart == null || segmentEnd == null) {
            return;
        }

        AABB expanded = target.getBoundingBox().inflate(config.chargeHitboxExpandBlocks);
        boolean directIntersect = expanded.clip(segmentStart, segmentEnd).isPresent();
        double leviathanRadius = Math.max(0.4d, leviathan.getBbWidth() * 0.5d);
        double targetRadius = Math.max(0.3d, target.getBbWidth() * 0.5d);
        double sweptRadius = leviathanRadius + targetRadius + config.chargeHitboxExpandBlocks;
        double centerDistanceSq = squaredDistancePointToSegment(target.getBoundingBox().getCenter(), segmentStart, segmentEnd);
        if (!directIntersect && centerDistanceSq > (sweptRadius * sweptRadius)) {
            return;
        }

        Vec3 toTarget = posOf(target).subtract(posOf(leviathan));
        double toTargetLen = toTarget.length();
        if (toTargetLen < 1.0e-6d) {
            return;
        }

        Vec3 toTargetNorm = toTarget.scale(1.0d / toTargetLen);
        Vec3 forward = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double dot;
        double toTargetHorizontalSq = (toTargetNorm.x * toTargetNorm.x) + (toTargetNorm.z * toTargetNorm.z);
        double forwardHorizontalSq = (forward.x * forward.x) + (forward.z * forward.z);
        if (toTargetHorizontalSq >= 1.0e-6d && forwardHorizontalSq >= 1.0e-6d) {
            Vec3 toTargetHorizontal = normalizeXZ(toTargetNorm.x, toTargetNorm.z);
            Vec3 forwardHorizontal = normalizeXZ(forward.x, forward.z);
            dot = forwardHorizontal.dot(toTargetHorizontal);
        } else {
            dot = forward.dot(toTargetNorm);
        }
        if (dot < config.chargeDirectionDotThreshold) {
            return;
        }

        double sampledSpeed = Math.max(leviathan.getDeltaMovement().length(), segmentEnd.distanceTo(segmentStart));
        double effectiveMinHitSpeed = Math.min(config.chargeMinHitSpeed, Math.max(0.1d, config.chargeSpeedBlocksPerTick * 0.35d));
        if (sampledSpeed < effectiveMinHitSpeed) {
            return;
        }

        long lastHitPass = runtime.lastHitPassByPlayer.getOrDefault(target.getUUID(), -1L);
        if (lastHitPass == runtime.passId) {
            return;
        }
        long lastHitTick = runtime.lastHitTickByPlayer.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2L);
        if ((serverTicks - lastHitTick) < config.chargeHitCooldownTicks) {
            return;
        }

        boolean damaged;
        double effectiveDamage = resolveEffectiveChargeDamage(leviathan, config, virtualStore);
        double totalHealthBefore = target.getHealth() + target.getAbsorptionAmount();
        if (leviathan instanceof LivingEntity living) {
            damaged = target.hurtServer(world, world.damageSources().mobAttack(living), (float) effectiveDamage);
        } else {
            damaged = target.hurtServer(world, world.damageSources().generic(), (float) effectiveDamage);
        }

        if (!damaged) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge attempted but rejected target={} raw={} speed={} (likely shield/blocking or hurt cooldown)",
                shortId(target.getUUID()),
                round2(effectiveDamage),
                round2(sampledSpeed));
            return;
        }

        double totalHealthAfter = target.getHealth() + target.getAbsorptionAmount();
        double appliedDamage = Math.max(0.0d, totalHealthBefore - totalHealthAfter);
        double mitigatedDamage = Math.max(0.0d, effectiveDamage - appliedDamage);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge mitigation target={} raw={} applied={} mitigated={} armor={} toughness={} absorptionBefore={} absorptionAfter={}",
            shortId(target.getUUID()),
            round2(effectiveDamage),
            round2(appliedDamage),
            round2(mitigatedDamage),
            round2(target.getArmorValue()),
            round2(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),
            round2(totalHealthBefore - target.getHealth()),
            round2(totalHealthAfter - target.getHealth()));

        if (config.chargeKnockbackStrength > 0.0d) {
            Vec3 knock = forward.scale(config.chargeKnockbackStrength);
            target.push(knock.x, Math.max(0.15d, knock.y + 0.15d), knock.z);
            target.hurtMarked = true;
        }

        runtime.lastHitTickByPlayer.put(target.getUUID(), serverTicks);
        runtime.lastHitPassByPlayer.put(target.getUUID(), runtime.passId);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge hit leviathan={} target={} damage={} speed={} passId={}",
            shortId(leviathan.getUUID()),
            shortId(target.getUUID()),
            round2(effectiveDamage),
            round2(sampledSpeed),
            runtime.passId);
    }

    private static double resolveEffectiveChargeDamage(Entity leviathan,
                                                       LeviathansConfig config,
                                                       VirtualLeviathanStore virtualStore) {
        Optional<UUID> id = LeviathanIdTags.getId(leviathan);
        if (id.isPresent() && virtualStore != null) {
            VirtualLeviathanStore.VirtualLeviathanState state = virtualStore.get(id.get());
            if (state != null) {
                return config.chargeDamage * state.damageMultiplier();
            }
        }
        return config.chargeDamage;
    }

    private static double squaredDistancePointToSegment(Vec3 point, Vec3 segmentStart, Vec3 segmentEnd) {
        Vec3 segment = segmentEnd.subtract(segmentStart);
        double segmentLenSq = segment.lengthSqr();
        if (segmentLenSq < 1.0e-12d) {
            return point.distanceToSqr(segmentStart);
        }
        double t = point.subtract(segmentStart).dot(segment) / segmentLenSq;
        t = clamp(t, 0.0d, 1.0d);
        Vec3 closest = segmentStart.add(segment.scale(t));
        return point.distanceToSqr(closest);
    }

    private static void circleAroundAnchor(ServerLevel world,
                                           Entity entity,
                                           Vec3 anchor,
                                           double radius,
                                           double speed,
                                           long serverTicks,
                                           LeviathansConfig config) {
        double safeRadius = Math.max(8.0d, radius);
        double angle = (serverTicks % 3600L) * 0.055d;
        Vec3 orbitPoint = new Vec3(
            anchor.x + Math.cos(angle) * safeRadius,
            anchor.y,
            anchor.z + Math.sin(angle) * safeRadius
        );

        Vec3 desired = orbitPoint.subtract(posOf(entity));
        if (desired.length() < 1.0e-6d) {
            return;
        }

        Vec3 steered = chooseSubmergedDirection(world, posOf(entity), desired.normalize(), false, config);
        entity.setDeltaMovement(steered.scale(Math.max(0.01d, speed)));
        faceAlong(entity, steered);
    }

    private static ServerPlayer findNearestTarget(ServerLevel world, Vec3 pos, int horizontalRadius, int verticalRadius) {
        double bestSq = Double.POSITIVE_INFINITY;
        ServerPlayer best = null;
        double maxHorizontalSq = (double) horizontalRadius * (double) horizontalRadius;
        double maxVertical = Math.max(1.0d, (double) verticalRadius);

        for (ServerPlayer player : world.players()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Vec3 playerPos = posOf(player);
            double dx = playerPos.x - pos.x;
            double dz = playerPos.z - pos.z;
            double horizontalSq = dx * dx + dz * dz;
            if (horizontalSq > maxHorizontalSq) {
                continue;
            }
            double dy = Math.abs(playerPos.y - pos.y);
            if (dy > maxVertical) {
                continue;
            }
            if (horizontalSq < bestSq) {
                bestSq = horizontalSq;
                best = player;
            }
        }

        return best;
    }

    private static boolean isWithinEngagementRange(Vec3 from, Vec3 to, int horizontalRadius, int verticalRadius) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horizontalSq = dx * dx + dz * dz;
        double maxHorizontalSq = (double) horizontalRadius * (double) horizontalRadius;
        if (horizontalSq > maxHorizontalSq) {
            return false;
        }
        double dy = Math.abs(to.y - from.y);
        return dy <= Math.max(1.0d, (double) verticalRadius);
    }

    private static boolean hasLineOfEngagement(ServerLevel world, Entity from, ServerPlayer target) {
        HitResult hit = world.clip(new ClipContext(
            from.getEyePosition(),
            target.getEyePosition(),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            from
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean hasLineOfEngagementCached(ServerLevel world,
                                                     Entity from,
                                                     ServerPlayer target,
                                                     LeviathanCombatRuntime runtime,
                                                     Vec3 entityPos,
                                                     Vec3 targetPos,
                                                     long serverTicks) {
        if (runtime.lastLineCheckEntityPos != null
            && runtime.lastLineCheckTargetPos != null
            && serverTicks < runtime.nextLineOfEngagementCheckTick
            && entityPos.distanceToSqr(runtime.lastLineCheckEntityPos) <= LINE_OF_ENGAGEMENT_ENTITY_MOVE_CACHE_SQ
            && targetPos.distanceToSqr(runtime.lastLineCheckTargetPos) <= LINE_OF_ENGAGEMENT_TARGET_MOVE_CACHE_SQ) {
            return runtime.lastLineOfEngagementValid;
        }

        boolean valid = hasLineOfEngagement(world, from, target);
        runtime.lastLineOfEngagementValid = valid;
        runtime.lastLineCheckEntityPos = entityPos;
        runtime.lastLineCheckTargetPos = targetPos;
        runtime.nextLineOfEngagementCheckTick = serverTicks + LINE_OF_ENGAGEMENT_CACHE_TICKS;
        return valid;
    }

    private static Vec3 chooseSubmergedDirectionWithCache(ServerLevel world,
                                                           Vec3 start,
                                                           Vec3 preferred,
                                                           LeviathansConfig config,
                                                           LeviathanCombatRuntime runtime,
                                                           long serverTicks) {
        int probeDistance = computeProbeDistance(true, config);
        if (runtime.lastAggressiveSteerDirection != null
            && (serverTicks - runtime.lastAggressiveSteerTick) <= AGGRESSIVE_STEER_CACHE_TICKS
            && pathIsSubmergedClear(world, start, runtime.lastAggressiveSteerDirection, probeDistance, config)) {
            return runtime.lastAggressiveSteerDirection;
        }

        Vec3 steered = chooseSubmergedDirection(world, start, preferred, true, config);
        runtime.lastAggressiveSteerDirection = steered;
        runtime.lastAggressiveSteerTick = serverTicks;
        return steered;
    }

    private static Vec3 chooseSubmergedDirection(ServerLevel world,
                                                  Vec3 start,
                                                  Vec3 preferred,
                                                  boolean aggressiveAvoidance,
                                                  LeviathansConfig config) {
        Vec3 normalized = normalizeVectorStrict(preferred);
        int probeDistance = computeProbeDistance(aggressiveAvoidance, config);

        if (pathIsSubmergedClear(world, start, normalized, probeDistance, config)) {
            return normalized;
        }

        double baseYaw = Math.atan2(normalized.z, normalized.x);
        int[] yawOffsets = aggressiveAvoidance
            ? new int[] {15, -15, 30, -30, 45, -45, 65, -65, 90, -90, 120, -120, 150, -150, 170, -170}
            : new int[] {20, -20, 40, -40, 60, -60, 80, -80, 110, -110, 150, -150};
        double[] verticalOffsets = aggressiveAvoidance
            ? new double[] {0.0, 0.25, -0.25, 0.45, -0.45, 0.65, -0.65, 0.8, -0.8}
            : new double[] {0.0, 0.2, -0.2, 0.35, -0.35, 0.5, -0.5};

        for (int yawOffset : yawOffsets) {
            double yaw = baseYaw + Math.toRadians(yawOffset * config.solidAvoidanceTurnStrength);
            for (double vertical : verticalOffsets) {
                Vec3 candidate = normalizeVectorStrict(new Vec3(Math.cos(yaw), vertical, Math.sin(yaw)));
                if (pathIsSubmergedClear(world, start, candidate, probeDistance, config)) {
                    return candidate;
                }
            }
        }

        if (aggressiveAvoidance) {
            Vec3 upForward = normalizeVectorStrict(new Vec3(normalized.x * 0.4d, 0.9d, normalized.z * 0.4d));
            if (pathIsSubmergedClear(world, start, upForward, probeDistance, config)) {
                return upForward;
            }

            Vec3 downForward = normalizeVectorStrict(new Vec3(normalized.x * 0.4d, -0.9d, normalized.z * 0.4d));
            if (pathIsSubmergedClear(world, start, downForward, probeDistance, config)) {
                return downForward;
            }

            Vec3 side = normalizeVectorStrict(new Vec3(-normalized.z, 0.0d, normalized.x));
            if (pathIsSubmergedClear(world, start, side, probeDistance, config)) {
                return side;
            }

            Vec3 oppositeSide = side.scale(-1.0d);
            if (pathIsSubmergedClear(world, start, oppositeSide, probeDistance, config)) {
                return oppositeSide;
            }
        }

        return normalized;
    }

    private static int computeProbeDistance(boolean aggressiveAvoidance, LeviathansConfig config) {
        int probeDistance = Math.max(1, config.solidAvoidanceProbeDistanceBlocks);
        if (!aggressiveAvoidance) {
            return probeDistance;
        }

        int chargeLookahead = Math.max(8, (int) Math.ceil(config.chargeSpeedBlocksPerTick * 12.0d));
        return Math.max(probeDistance, chargeLookahead);
    }

    private static boolean pathIsSubmergedClear(ServerLevel world,
                                                Vec3 start,
                                                Vec3 direction,
                                                int probeDistance,
                                                LeviathansConfig config) {
        int maxProbe = Math.max(1, probeDistance);
        int verticalClearance = Math.max(0, config.solidAvoidanceVerticalClearanceBlocks);

        for (int i = 1; i <= maxProbe; i++) {
            Vec3 sample = start.add(direction.scale(i));
            BlockPos base = BlockPos.containing(sample);

            if (!isChunkLoadedForBlock(world, base)) {
                return false;
            }

            if (config.requireWaterForSpawn && !world.getFluidState(base).is(Fluids.WATER)) {
                return false;
            }

            BlockState at = world.getBlockState(base);
            if (at.isRedstoneConductor(world, base) && !world.getFluidState(base).is(Fluids.WATER)) {
                return false;
            }

            for (int up = 1; up <= verticalClearance; up++) {
                BlockPos check = base.above(up);
                if (!isChunkLoadedForBlock(world, check)) {
                    return false;
                }
                BlockState bs = world.getBlockState(check);
                if (bs.isRedstoneConductor(world, check) && !world.getFluidState(check).is(Fluids.WATER)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static Vec3 turnLimited(Vec3 current, Vec3 desired, double maxTurnRadians) {
        double currentAngle = Math.atan2(current.z, current.x);
        double desiredAngle = Math.atan2(desired.z, desired.x);
        double delta = wrapRadians(desiredAngle - currentAngle);
        double limited = clamp(delta, -maxTurnRadians, maxTurnRadians);
        return normalizeXZ(Math.cos(currentAngle + limited), Math.sin(currentAngle + limited));
    }

    private static Vec3 normalizeVectorStrict(Vec3 vector) {
        if (vector == null || !Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalStateException("Non-finite direction vector");
        }
        double len = vector.length();
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Zero-length direction vector");
        }
        return vector.scale(1.0d / len);
    }

    private static void faceAlong(Entity entity, Vec3 direction) {
        Vec3 normalized = normalizeVectorStrict(direction);
        float yaw = (float) Math.toDegrees(Math.atan2(normalized.z, normalized.x)) - 90.0f;
        double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(normalized.y, horizontal));
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setXRot(pitch);
    }

    private static Vec3 normalizeXZ(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Heading normalization failed (zero vector)");
        }
        return new Vec3(x / len, 0.0, z / len);
    }

    private static boolean isChunkLoadedForBlock(ServerLevel world, BlockPos pos) {
        return world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static Vec3 posOf(Entity entity) {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }

    private static double wrapRadians(double radians) {
        while (radians > Math.PI) {
            radians -= (Math.PI * 2.0);
        }
        while (radians < -Math.PI) {
            radians += (Math.PI * 2.0);
        }
        return radians;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, 8);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
