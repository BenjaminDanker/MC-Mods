package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

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

    static boolean updateCombat(ServerWorld world,
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
            ServerPlayerEntity initialTarget = findNearestTarget(world, posOf(entity), config.engageRadiusBlocks, config.engageVerticalRadiusBlocks);
            if (initialTarget == null) {
                runtime.previousPos = posOf(entity);
                return false;
            }
            runtime.targetUuid = initialTarget.getUuid();
            runtime.anchorPos = posOf(initialTarget);
            runtime.phaseStartTick = serverTicks;
            runtime.substate = LeviathanCombatSubstate.ACQUIRE;
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement start leviathan={} target={}", shortId(entity.getUuid()), shortId(initialTarget.getUuid()));
        }

        ServerPlayerEntity target = world.getServer() == null ? null : world.getServer().getPlayerManager().getPlayer(runtime.targetUuid);
        Vec3d entityPos = posOf(entity);
        Vec3d previousPos = runtime.previousPos == null ? entityPos : runtime.previousPos;

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
                    shortId(entity.getUuid()),
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

        Vec3d targetPos = posOf(target);
        runtime.anchorPos = targetPos;
        runtime.missingTargetUntilTick = -1L;

        if (runtime.substate == LeviathanCombatSubstate.REACQUIRE
            && entityPos.distanceTo(targetPos) <= PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS) {
            if (serverTicks >= runtime.cooldownUntilTick) {
                AtlantisMod.LOGGER.info("[Atlantis][leviathan] predator retreat canceled by close target distance={} <= {} leviathan={} target={}",
                    round1(entityPos.distanceTo(targetPos)),
                    round1(PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS),
                    shortId(entity.getUuid()),
                    shortId(target.getUuid()));
                beginCharge(runtime, entityPos, target, serverTicks, config);
            } else {
                runtime.substate = LeviathanCombatSubstate.ACQUIRE;
                runtime.phaseStartTick = serverTicks;
            }
        }

        if (!isWithinEngagementRange(entityPos, targetPos, config.disengageRadiusBlocks, config.disengageVerticalRadiusBlocks)) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (out of range) leviathan={} target={} distance={} disengageRadius={}",
                shortId(entity.getUuid()),
                shortId(target.getUuid()),
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
                shortId(entity.getUuid()),
                shortId(target.getUuid()),
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
                Vec3d guidedDirection = refineChargeDirection(runtime, entityPos, target, 6.0d, 0.045d, config);
                runtime.chargeDirection = guidedDirection;
                detectChargeHit(world, entity, target, runtime, previousPos, entityPos, config, serverTicks, virtualStore);
                Vec3d steered = chooseSubmergedDirectionWithCache(world, entityPos, guidedDirection, config, runtime, serverTicks);
                entity.setVelocity(steered.multiply(config.chargeSpeedBlocksPerTick));
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
                Vec3d steered = chooseSubmergedDirectionWithCache(world, entityPos, runtime.chargeDirection, config, runtime, serverTicks);
                entity.setVelocity(steered.multiply(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if (detectChargeStall(entity, runtime, previousPos, entityPos, config, serverTicks)) {
                    break;
                }

                if (runtime.passStartPos == null) {
                    runtime.passStartPos = previousPos == null ? entityPos : previousPos;
                }

                if (entityPos.distanceTo(runtime.passStartPos) >= PREDATOR_RETREAT_RADIUS_BLOCKS) {
                    startPredatorRetreat(runtime, entityPos, targetPos, target.getUuid(), serverTicks);
                }
            }
            case TURN_BACK -> predatorRetreatStep(world, entity, runtime, targetPos, target.getUuid(), serverTicks, config);
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

    static void applyLoadedPassiveMovement(ServerWorld world,
                                           Entity entity,
                                           VirtualLeviathanStore.VirtualLeviathanState state,
                                           LeviathansConfig config) {
        Vec3d heading = normalizeXZ(state.headingX(), state.headingZ());
        Vec3d steered = chooseSubmergedDirection(world, posOf(entity), heading, false, config);
        double speed = Math.max(0.01d, config.virtualSpeedBlocksPerTick);

        entity.setVelocity(steered.x * speed, steered.y * speed, steered.z * speed);
        faceAlong(entity, steered);
    }

    private static void beginCharge(LeviathanCombatRuntime runtime,
                                    Vec3d entityPos,
                                    ServerPlayerEntity target,
                                    long serverTicks,
                                    LeviathansConfig config) {
        Vec3d intercept = posOf(target).add(target.getVelocity().multiply(Math.min(10.0d, config.chargeDurationTicks * 0.25d)));
        Vec3d raw = intercept.subtract(entityPos);
        double len = raw.length();
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Charge direction invalid (zero length) for target=" + target.getUuidAsString());
        }

        runtime.chargeDirection = raw.multiply(1.0d / len);
        runtime.substate = LeviathanCombatSubstate.CHARGE;
        runtime.phaseStartTick = serverTicks;
        runtime.stalledTicks = 0L;
        runtime.lastAggressiveSteerDirection = null;
        runtime.lastAggressiveSteerTick = Long.MIN_VALUE;
        runtime.passId++;
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] charge begin target={} passId={} direction=({}, {}, {})",
            shortId(target.getUuid()),
            runtime.passId,
            round2(runtime.chargeDirection.x),
            round2(runtime.chargeDirection.y),
            round2(runtime.chargeDirection.z));
    }

    private static Vec3d refineChargeDirection(LeviathanCombatRuntime runtime,
                                               Vec3d entityPos,
                                               ServerPlayerEntity target,
                                               double maxHorizontalTurnDegrees,
                                               double maxVerticalStep,
                                               LeviathansConfig config) {
        Vec3d current = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3d(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double lookaheadTicks = clamp(config.chargeDurationTicks * 0.15d, 2.0d, 7.0d);
        Vec3d intercept = posOf(target).add(target.getVelocity().multiply(lookaheadTicks));
        Vec3d toIntercept = intercept.subtract(entityPos);
        double len = toIntercept.length();
        if (len < 1.0e-6d) {
            return current;
        }

        Vec3d desired = toIntercept.multiply(1.0d / len);
        double maxTurnRadians = Math.toRadians(Math.max(0.5d, maxHorizontalTurnDegrees));
        return steerChargeDirection3d(current, desired, maxTurnRadians, Math.max(0.01d, maxVerticalStep));
    }

    private static Vec3d steerChargeDirection3d(Vec3d current,
                                                Vec3d desired,
                                                double maxHorizontalTurnRadians,
                                                double maxVerticalStep) {
        Vec3d currentNorm = normalizeVectorStrict(current);
        Vec3d desiredNorm = normalizeVectorStrict(desired);

        Vec3d currentHorizontal = new Vec3d(currentNorm.x, 0.0d, currentNorm.z);
        Vec3d desiredHorizontal = new Vec3d(desiredNorm.x, 0.0d, desiredNorm.z);

        Vec3d steeredHorizontal;
        double currentHorizontalSq = currentHorizontal.lengthSquared();
        double desiredHorizontalSq = desiredHorizontal.lengthSquared();
        if (currentHorizontalSq < 1.0e-6d && desiredHorizontalSq < 1.0e-6d) {
            steeredHorizontal = new Vec3d(1.0d, 0.0d, 0.0d);
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
        return normalizeVectorStrict(new Vec3d(steeredHorizontal.x, steeredY, steeredHorizontal.z));
    }

    private static boolean detectChargeStall(Entity entity,
                                             LeviathanCombatRuntime runtime,
                                             Vec3d previousPos,
                                             Vec3d currentPos,
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

        double movedSq = currentPos.squaredDistanceTo(previousPos);
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
            shortId(entity.getUuid()),
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
                                             Vec3d entityPos,
                                             Vec3d targetPos,
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

    private static Vec3d selectRetreatTarget(Vec3d entityPos, Vec3d targetPos, long passId, UUID targetId) {
        Vec3d fromTarget = new Vec3d(entityPos.x - targetPos.x, 0.0d, entityPos.z - targetPos.z);
        Vec3d base;
        try {
            base = normalizeXZ(fromTarget.x, fromTarget.z);
        } catch (IllegalStateException ignored) {
            base = new Vec3d(1.0d, 0.0d, 0.0d);
        }

        long seed = passId ^ targetId.getLeastSignificantBits() ^ targetId.getMostSignificantBits();
        Random random = new Random(seed);
        double angleJitter = (random.nextDouble() * Math.PI * 1.2d) - (Math.PI * 0.6d);
        double baseAngle = Math.atan2(base.z, base.x) + angleJitter;

        double tx = targetPos.x + Math.cos(baseAngle) * PREDATOR_RETREAT_RADIUS_BLOCKS;
        double tz = targetPos.z + Math.sin(baseAngle) * PREDATOR_RETREAT_RADIUS_BLOCKS;
        return new Vec3d(tx, targetPos.y, tz);
    }

    private static void predatorRetreatStep(ServerWorld world,
                                            Entity entity,
                                            LeviathanCombatRuntime runtime,
                                            Vec3d targetPos,
                                            UUID targetId,
                                            long serverTicks,
                                            LeviathansConfig config) {
        Vec3d entityPos = posOf(entity);

        if (runtime.retreatTarget == null) {
            runtime.retreatTarget = selectRetreatTarget(entityPos, targetPos, runtime.passId, targetId);
        }
        if (runtime.retreatCenter == null) {
            runtime.retreatCenter = entityPos;
        }

        Vec3d retreatTarget = new Vec3d(runtime.retreatTarget.x, entityPos.y, runtime.retreatTarget.z);
        Vec3d toRetreat = retreatTarget.subtract(entityPos);
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
                shortId(entity.getUuid()));
            return;
        }

        Vec3d centerToEntity = new Vec3d(entityPos.x - runtime.retreatCenter.x, 0.0d, entityPos.z - runtime.retreatCenter.z);
        Vec3d tangent;
        try {
            Vec3d radial = normalizeXZ(centerToEntity.x, centerToEntity.z);
            tangent = new Vec3d(-radial.z, 0.0d, radial.x);
        } catch (IllegalStateException ignored) {
            tangent = new Vec3d(0.0d, 0.0d, 1.0d);
        }

        Vec3d toward;
        try {
            toward = normalizeXZ(toRetreat.x, toRetreat.z);
        } catch (IllegalStateException ignored) {
            toward = tangent;
        }
        Vec3d desired = normalizeVectorStrict(toward.multiply(0.75d).add(tangent.multiply(0.45d)));

        Vec3d steered = chooseSubmergedDirectionWithCache(world, entityPos, desired, config, runtime, serverTicks);
        double recuperateSpeed = Math.max(0.06d, Math.min(config.virtualSpeedBlocksPerTick, config.chargeSpeedBlocksPerTick * 0.5d));
        entity.setVelocity(steered.multiply(recuperateSpeed));
        faceAlong(entity, steered);
    }

    private static void predatorCircleStep(ServerWorld world,
                                           Entity entity,
                                           LeviathanCombatRuntime runtime,
                                           long serverTicks,
                                           LeviathansConfig config) {
        Vec3d entityPos = posOf(entity);
        Vec3d center = runtime.retreatTarget == null ? entityPos : runtime.retreatTarget;

        double baseRadius = PREDATOR_CIRCLE_RADIUS_BLOCKS;
        double angleSpeed = 0.05d;
        double elapsed = Math.max(0.0d, (double) (serverTicks - runtime.phaseStartTick));
        double angle = elapsed * angleSpeed;
        if ((runtime.passId & 1L) == 0L) {
            angle *= -1.0d;
        }

        Vec3d orbitPoint = new Vec3d(
            center.x + Math.cos(angle) * baseRadius,
            entityPos.y,
            center.z + Math.sin(angle) * baseRadius
        );
        Vec3d desired = orbitPoint.subtract(entityPos);
        if (desired.length() < 1.0e-6d) {
            desired = new Vec3d(1.0d, 0.0d, 0.0d);
        }

        Vec3d steered = chooseSubmergedDirectionWithCache(world, entityPos, desired.normalize(), config, runtime, serverTicks);
        double recuperateSpeed = Math.max(0.06d, Math.min(config.virtualSpeedBlocksPerTick, config.chargeSpeedBlocksPerTick * 0.45d));
        entity.setVelocity(steered.multiply(recuperateSpeed));
        faceAlong(entity, steered);
    }

    private static void detectChargeHit(ServerWorld world,
                                        Entity leviathan,
                                        ServerPlayerEntity target,
                                        LeviathanCombatRuntime runtime,
                                        Vec3d segmentStart,
                                        Vec3d segmentEnd,
                                        LeviathansConfig config,
                                        long serverTicks,
                                        VirtualLeviathanStore virtualStore) {
        if (segmentStart == null || segmentEnd == null) {
            return;
        }

        Box expanded = target.getBoundingBox().expand(config.chargeHitboxExpandBlocks);
        boolean directIntersect = expanded.raycast(segmentStart, segmentEnd).isPresent();
        double leviathanRadius = Math.max(0.4d, leviathan.getWidth() * 0.5d);
        double targetRadius = Math.max(0.3d, target.getWidth() * 0.5d);
        double sweptRadius = leviathanRadius + targetRadius + config.chargeHitboxExpandBlocks;
        double centerDistanceSq = squaredDistancePointToSegment(target.getBoundingBox().getCenter(), segmentStart, segmentEnd);
        if (!directIntersect && centerDistanceSq > (sweptRadius * sweptRadius)) {
            return;
        }

        Vec3d toTarget = posOf(target).subtract(posOf(leviathan));
        double toTargetLen = toTarget.length();
        if (toTargetLen < 1.0e-6d) {
            return;
        }

        Vec3d toTargetNorm = toTarget.multiply(1.0d / toTargetLen);
        Vec3d forward = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3d(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double dot;
        double toTargetHorizontalSq = (toTargetNorm.x * toTargetNorm.x) + (toTargetNorm.z * toTargetNorm.z);
        double forwardHorizontalSq = (forward.x * forward.x) + (forward.z * forward.z);
        if (toTargetHorizontalSq >= 1.0e-6d && forwardHorizontalSq >= 1.0e-6d) {
            Vec3d toTargetHorizontal = normalizeXZ(toTargetNorm.x, toTargetNorm.z);
            Vec3d forwardHorizontal = normalizeXZ(forward.x, forward.z);
            dot = forwardHorizontal.dotProduct(toTargetHorizontal);
        } else {
            dot = forward.dotProduct(toTargetNorm);
        }
        if (dot < config.chargeDirectionDotThreshold) {
            return;
        }

        double sampledSpeed = Math.max(leviathan.getVelocity().length(), segmentEnd.distanceTo(segmentStart));
        double effectiveMinHitSpeed = Math.min(config.chargeMinHitSpeed, Math.max(0.1d, config.chargeSpeedBlocksPerTick * 0.35d));
        if (sampledSpeed < effectiveMinHitSpeed) {
            return;
        }

        long lastHitPass = runtime.lastHitPassByPlayer.getOrDefault(target.getUuid(), -1L);
        if (lastHitPass == runtime.passId) {
            return;
        }
        long lastHitTick = runtime.lastHitTickByPlayer.getOrDefault(target.getUuid(), Long.MIN_VALUE / 2L);
        if ((serverTicks - lastHitTick) < config.chargeHitCooldownTicks) {
            return;
        }

        boolean damaged;
        double effectiveDamage = resolveEffectiveChargeDamage(leviathan, config, virtualStore);
        double totalHealthBefore = target.getHealth() + target.getAbsorptionAmount();
        if (leviathan instanceof LivingEntity living) {
            damaged = target.damage(world, world.getDamageSources().mobAttack(living), (float) effectiveDamage);
        } else {
            damaged = target.damage(world, world.getDamageSources().generic(), (float) effectiveDamage);
        }

        if (!damaged) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge attempted but rejected target={} raw={} speed={} (likely shield/blocking or hurt cooldown)",
                shortId(target.getUuid()),
                round2(effectiveDamage),
                round2(sampledSpeed));
            return;
        }

        double totalHealthAfter = target.getHealth() + target.getAbsorptionAmount();
        double appliedDamage = Math.max(0.0d, totalHealthBefore - totalHealthAfter);
        double mitigatedDamage = Math.max(0.0d, effectiveDamage - appliedDamage);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge mitigation target={} raw={} applied={} mitigated={} armor={} toughness={} absorptionBefore={} absorptionAfter={}",
            shortId(target.getUuid()),
            round2(effectiveDamage),
            round2(appliedDamage),
            round2(mitigatedDamage),
            round2(target.getArmor()),
            round2(target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)),
            round2(totalHealthBefore - target.getHealth()),
            round2(totalHealthAfter - target.getHealth()));

        if (config.chargeKnockbackStrength > 0.0d) {
            Vec3d knock = forward.multiply(config.chargeKnockbackStrength);
            target.addVelocity(knock.x, Math.max(0.15d, knock.y + 0.15d), knock.z);
            target.velocityModified = true;
        }

        runtime.lastHitTickByPlayer.put(target.getUuid(), serverTicks);
        runtime.lastHitPassByPlayer.put(target.getUuid(), runtime.passId);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge hit leviathan={} target={} damage={} speed={} passId={}",
            shortId(leviathan.getUuid()),
            shortId(target.getUuid()),
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

    private static double squaredDistancePointToSegment(Vec3d point, Vec3d segmentStart, Vec3d segmentEnd) {
        Vec3d segment = segmentEnd.subtract(segmentStart);
        double segmentLenSq = segment.lengthSquared();
        if (segmentLenSq < 1.0e-12d) {
            return point.squaredDistanceTo(segmentStart);
        }
        double t = point.subtract(segmentStart).dotProduct(segment) / segmentLenSq;
        t = clamp(t, 0.0d, 1.0d);
        Vec3d closest = segmentStart.add(segment.multiply(t));
        return point.squaredDistanceTo(closest);
    }

    private static void circleAroundAnchor(ServerWorld world,
                                           Entity entity,
                                           Vec3d anchor,
                                           double radius,
                                           double speed,
                                           long serverTicks,
                                           LeviathansConfig config) {
        double safeRadius = Math.max(8.0d, radius);
        double angle = (serverTicks % 3600L) * 0.055d;
        Vec3d orbitPoint = new Vec3d(
            anchor.x + Math.cos(angle) * safeRadius,
            anchor.y,
            anchor.z + Math.sin(angle) * safeRadius
        );

        Vec3d desired = orbitPoint.subtract(posOf(entity));
        if (desired.length() < 1.0e-6d) {
            return;
        }

        Vec3d steered = chooseSubmergedDirection(world, posOf(entity), desired.normalize(), false, config);
        entity.setVelocity(steered.multiply(Math.max(0.01d, speed)));
        faceAlong(entity, steered);
    }

    private static ServerPlayerEntity findNearestTarget(ServerWorld world, Vec3d pos, int horizontalRadius, int verticalRadius) {
        double bestSq = Double.POSITIVE_INFINITY;
        ServerPlayerEntity best = null;
        double maxHorizontalSq = (double) horizontalRadius * (double) horizontalRadius;
        double maxVertical = Math.max(1.0d, (double) verticalRadius);

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Vec3d playerPos = posOf(player);
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

    private static boolean isWithinEngagementRange(Vec3d from, Vec3d to, int horizontalRadius, int verticalRadius) {
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

    private static boolean hasLineOfEngagement(ServerWorld world, Entity from, ServerPlayerEntity target) {
        HitResult hit = world.raycast(new RaycastContext(
            from.getEyePos(),
            target.getEyePos(),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            from
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean hasLineOfEngagementCached(ServerWorld world,
                                                     Entity from,
                                                     ServerPlayerEntity target,
                                                     LeviathanCombatRuntime runtime,
                                                     Vec3d entityPos,
                                                     Vec3d targetPos,
                                                     long serverTicks) {
        if (runtime.lastLineCheckEntityPos != null
            && runtime.lastLineCheckTargetPos != null
            && serverTicks < runtime.nextLineOfEngagementCheckTick
            && entityPos.squaredDistanceTo(runtime.lastLineCheckEntityPos) <= LINE_OF_ENGAGEMENT_ENTITY_MOVE_CACHE_SQ
            && targetPos.squaredDistanceTo(runtime.lastLineCheckTargetPos) <= LINE_OF_ENGAGEMENT_TARGET_MOVE_CACHE_SQ) {
            return runtime.lastLineOfEngagementValid;
        }

        boolean valid = hasLineOfEngagement(world, from, target);
        runtime.lastLineOfEngagementValid = valid;
        runtime.lastLineCheckEntityPos = entityPos;
        runtime.lastLineCheckTargetPos = targetPos;
        runtime.nextLineOfEngagementCheckTick = serverTicks + LINE_OF_ENGAGEMENT_CACHE_TICKS;
        return valid;
    }

    private static Vec3d chooseSubmergedDirectionWithCache(ServerWorld world,
                                                           Vec3d start,
                                                           Vec3d preferred,
                                                           LeviathansConfig config,
                                                           LeviathanCombatRuntime runtime,
                                                           long serverTicks) {
        int probeDistance = computeProbeDistance(true, config);
        if (runtime.lastAggressiveSteerDirection != null
            && (serverTicks - runtime.lastAggressiveSteerTick) <= AGGRESSIVE_STEER_CACHE_TICKS
            && pathIsSubmergedClear(world, start, runtime.lastAggressiveSteerDirection, probeDistance, config)) {
            return runtime.lastAggressiveSteerDirection;
        }

        Vec3d steered = chooseSubmergedDirection(world, start, preferred, true, config);
        runtime.lastAggressiveSteerDirection = steered;
        runtime.lastAggressiveSteerTick = serverTicks;
        return steered;
    }

    private static Vec3d chooseSubmergedDirection(ServerWorld world,
                                                  Vec3d start,
                                                  Vec3d preferred,
                                                  boolean aggressiveAvoidance,
                                                  LeviathansConfig config) {
        Vec3d normalized = normalizeVectorStrict(preferred);
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
                Vec3d candidate = normalizeVectorStrict(new Vec3d(Math.cos(yaw), vertical, Math.sin(yaw)));
                if (pathIsSubmergedClear(world, start, candidate, probeDistance, config)) {
                    return candidate;
                }
            }
        }

        if (aggressiveAvoidance) {
            Vec3d upForward = normalizeVectorStrict(new Vec3d(normalized.x * 0.4d, 0.9d, normalized.z * 0.4d));
            if (pathIsSubmergedClear(world, start, upForward, probeDistance, config)) {
                return upForward;
            }

            Vec3d downForward = normalizeVectorStrict(new Vec3d(normalized.x * 0.4d, -0.9d, normalized.z * 0.4d));
            if (pathIsSubmergedClear(world, start, downForward, probeDistance, config)) {
                return downForward;
            }

            Vec3d side = normalizeVectorStrict(new Vec3d(-normalized.z, 0.0d, normalized.x));
            if (pathIsSubmergedClear(world, start, side, probeDistance, config)) {
                return side;
            }

            Vec3d oppositeSide = side.multiply(-1.0d);
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

    private static boolean pathIsSubmergedClear(ServerWorld world,
                                                Vec3d start,
                                                Vec3d direction,
                                                int probeDistance,
                                                LeviathansConfig config) {
        int maxProbe = Math.max(1, probeDistance);
        int verticalClearance = Math.max(0, config.solidAvoidanceVerticalClearanceBlocks);

        for (int i = 1; i <= maxProbe; i++) {
            Vec3d sample = start.add(direction.multiply(i));
            BlockPos base = BlockPos.ofFloored(sample);

            if (!isChunkLoadedForBlock(world, base)) {
                return false;
            }

            if (config.requireWaterForSpawn && !world.getFluidState(base).isOf(Fluids.WATER)) {
                return false;
            }

            BlockState at = world.getBlockState(base);
            if (at.isSolidBlock(world, base) && !world.getFluidState(base).isOf(Fluids.WATER)) {
                return false;
            }

            for (int up = 1; up <= verticalClearance; up++) {
                BlockPos check = base.up(up);
                if (!isChunkLoadedForBlock(world, check)) {
                    return false;
                }
                BlockState bs = world.getBlockState(check);
                if (bs.isSolidBlock(world, check) && !world.getFluidState(check).isOf(Fluids.WATER)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static Vec3d turnLimited(Vec3d current, Vec3d desired, double maxTurnRadians) {
        double currentAngle = Math.atan2(current.z, current.x);
        double desiredAngle = Math.atan2(desired.z, desired.x);
        double delta = wrapRadians(desiredAngle - currentAngle);
        double limited = clamp(delta, -maxTurnRadians, maxTurnRadians);
        return normalizeXZ(Math.cos(currentAngle + limited), Math.sin(currentAngle + limited));
    }

    private static Vec3d normalizeVectorStrict(Vec3d vector) {
        if (vector == null || !Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalStateException("Non-finite direction vector");
        }
        double len = vector.length();
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Zero-length direction vector");
        }
        return vector.multiply(1.0d / len);
    }

    private static void faceAlong(Entity entity, Vec3d direction) {
        Vec3d normalized = normalizeVectorStrict(direction);
        float yaw = (float) Math.toDegrees(Math.atan2(normalized.z, normalized.x)) - 90.0f;
        double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(normalized.y, horizontal));
        entity.setYaw(yaw);
        entity.setBodyYaw(yaw);
        entity.setHeadYaw(yaw);
        entity.setPitch(pitch);
    }

    private static Vec3d normalizeXZ(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Heading normalization failed (zero vector)");
        }
        return new Vec3d(x / len, 0.0, z / len);
    }

    private static boolean isChunkLoadedForBlock(ServerWorld world, BlockPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static Vec3d posOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
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
