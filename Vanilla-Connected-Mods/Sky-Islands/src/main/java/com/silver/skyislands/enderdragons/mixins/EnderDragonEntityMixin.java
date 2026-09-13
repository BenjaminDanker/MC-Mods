package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.DragonIdTags;
import com.silver.skyislands.enderdragons.DragonProvokedAccess;
import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EnderDragon.class)
public abstract class EnderDragonEntityMixin implements DragonProvokedAccess {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(EnderDragonEntityMixin.class);

    @Unique
    private boolean skyIslands$provokedByPlayer;

    @Unique
    private boolean skyIslands$deathHandled;

    @Unique
    private UUID skyIslands$provokingPlayerUuid;

    @Unique
    private long skyIslands$nextChargeTick;

    @Unique
    private long skyIslands$nextBreathTick;

    @Unique
    private int skyIslands$chargeTicksRemaining;

    @Unique
    private double skyIslands$chargeDirX;

    @Unique
    private double skyIslands$chargeDirY;

    @Unique
    private double skyIslands$chargeDirZ;

    @Unique
    private long skyIslands$flybyUntilTick;

    @Unique
    private double skyIslands$flybyDirX;

    @Unique
    private double skyIslands$flybyDirZ;

    @Unique
    private Vec3 skyIslands$currentHorizontalDir(EnderDragon dragon) {
        Vec3 v = dragon.getDeltaMovement();
        Vec3 h = new Vec3(v.x, 0.0, v.z);
        double len = h.length();
        if (len > 1.0e-3) {
            return h.scale(1.0 / len);
        }

        double yawRad = Math.toRadians(dragon.getYRot());
        return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }

    @Unique
    private Vec3 skyIslands$turnLimitedDir(EnderDragon dragon, Vec3 desiredDir, double maxTurnRad) {
        Vec3 desiredH = new Vec3(desiredDir.x, 0.0, desiredDir.z);
        double dLen = desiredH.length();
        if (dLen < 1.0e-6) {
            return desiredDir;
        }
        desiredH = desiredH.scale(1.0 / dLen);

        Vec3 currentH = skyIslands$currentHorizontalDir(dragon);
        double curAng = Math.atan2(currentH.z, currentH.x);
        double desAng = Math.atan2(desiredH.z, desiredH.x);

        double delta = Math.toRadians(Mth.wrapDegrees((float) Math.toDegrees(desAng - curAng)));
        double limited = Mth.clamp(delta, -maxTurnRad, maxTurnRad);
        double newAng = curAng + limited;

        double nx = Math.cos(newAng);
        double nz = Math.sin(newAng);

        double y = Mth.clamp(desiredDir.y, -0.6, 0.6);
        Vec3 combined = new Vec3(nx, y, nz);
        double len = combined.length();
        if (len > 1.0e-6) {
            combined = combined.scale(1.0 / len);
        }
        return combined;
    }

    @Unique
    private void skyIslands$faceDirection(EnderDragon dragon, Vec3 dir) {
        double lenSq = dir.x * dir.x + dir.y * dir.y + dir.z * dir.z;
        if (lenSq < 1.0e-6) {
            return;
        }

        // The dragon model orientation is effectively flipped relative to the standard yaw formula,
        // so add 180 degrees so it visually faces the direction it is traveling.
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)) + 180.0;
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double pitch = -Math.toDegrees(Math.atan2(dir.y, Math.max(1.0e-6, horiz)));

        float desiredYaw = (float) yaw;
        float desiredPitch = (float) pitch;

        float currentYaw = dragon.getYRot();
        float currentPitch = dragon.getXRot();

        float yawDelta = Mth.wrapDegrees(desiredYaw - currentYaw);
        float pitchDelta = desiredPitch - currentPitch;

        float maxYawStep = 10.0f;
        float maxPitchStep = 6.0f;

        float newYaw = currentYaw + Mth.clamp(yawDelta, -maxYawStep, maxYawStep);
        float newPitch = currentPitch + Mth.clamp(pitchDelta, -maxPitchStep, maxPitchStep);

        dragon.setYRot(newYaw);
        dragon.setYBodyRot(newYaw);
        dragon.setYHeadRot(newYaw);
        dragon.setXRot(newPitch);
    }

    @Override
    public boolean skyIslands$isProvoked() {
        return this.skyIslands$provokedByPlayer;
    }

    @Unique
    private void skyIslands$maybeMarkProvoked(ServerLevel world, DamageSource source) {
        if (this.skyIslands$provokedByPlayer) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] already provoked uuid={}", ((EnderDragon) (Object) this).getStringUUID());
            }
            return;
        }

        EnderDragon dragon = (EnderDragon) (Object) this;
        if (dragon.getHealth() <= (dragon.getMaxHealth() / 8.0f)) {
            // At very low health, the dragon disengages and will not re-aggro.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] ignore provoke at lowHP uuid={} hp={} max={}",
                        dragon.getStringUUID(), dragon.getHealth(), dragon.getMaxHealth());
            }
            return;
        }

        Player player = null;

        Entity attacker = source.getEntity();
        if (attacker instanceof Player p) {
            player = p;
        } else {
            Entity direct = source.getDirectEntity();
            if (direct instanceof Player p) {
                player = p;
            } else if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player p) {
                player = p;
            }
        }

        if (player == null) {
            if (LOGGER.isDebugEnabled()) {
                Entity direct = source.getDirectEntity();
                LOGGER.debug("[Sky-Islands][dragons][combat] ignore provoke (no player) uuid={} attacker={} direct={}",
                        dragon.getStringUUID(),
                        attacker != null ? attacker.getType().toString() : "<null>",
                        direct != null ? direct.getType().toString() : "<null>");
            }
            return;
        }

        this.skyIslands$provokedByPlayer = true;
        this.skyIslands$provokingPlayerUuid = player.getUUID();

        long now = world.getGameTime();
        this.skyIslands$nextChargeTick = now + 60;
        this.skyIslands$nextBreathTick = now + 40;
        this.skyIslands$chargeTicksRemaining = 0;

        // Make the dragon react immediately instead of waiting for vanilla phase selection.
        dragon.setFightOrigin(player.blockPosition());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][combat] provoked uuid={} byPlayer={} playerUuid={} damageType={}",
                    dragon.getStringUUID(),
                    player.getScoreboardName(),
                    player.getStringUUID(),
                    String.valueOf(source.getMsgId()));
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void skyIslands$markProvokedByPlayer(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        skyIslands$maybeMarkProvoked(world, source);
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
    private void skyIslands$markProvokedByPlayerFromPart(ServerLevel world, net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        skyIslands$maybeMarkProvoked(world, source);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void skyIslands$customProvokedCombat(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (!(dragon.level() instanceof ServerLevel world)) {
            return;
        }
        if (!EnderDragonManager.isManaged(dragon)) {
            return;
        }
        boolean provoked = this.skyIslands$provokedByPlayer;

        // Disengage rule: once very low HP, return to roaming and never re-aggro.
        if (provoked && dragon.getHealth() <= (dragon.getMaxHealth() / 8.0f)) {
            this.skyIslands$provokedByPlayer = false;
            this.skyIslands$provokingPlayerUuid = null;
            this.skyIslands$chargeTicksRemaining = 0;
            this.skyIslands$nextChargeTick = 0;
            this.skyIslands$nextBreathTick = 0;
            this.skyIslands$flybyUntilTick = 0;
            provoked = false;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] disengage uuid={} lowHP hp={} max={}",
                        dragon.getStringUUID(), dragon.getHealth(), dragon.getMaxHealth());
            }
        }

        long now = world.getGameTime();

        // --- Passive steering (loaded, not provoked) ---
        // Note: This must run even when there is no provoking player UUID.
        if (!provoked) {
            var cfg = EnderDragonManager.getConfig();
            double base = cfg != null ? cfg.virtualSpeedBlocksPerTick : 0.6;
            double desiredSpeed = Math.max(0.25, Math.min(1.1, base));

            Vec3 newVel = null;
            var idOpt = DragonIdTags.getId(dragon);
            if (idOpt.isPresent()) {
                var s = EnderDragonManager.getVirtualState(idOpt.get());
                if (s != null) {
                    boolean headAvoidActive = EnderDragonManager.isHeadAvoidActive(idOpt.get());
                    Vec3 dir;
                    double targetY;

                    if (headAvoidActive) {
                        BlockPos origin = dragon.getFightOrigin();
                        Vec3 originCenter = new Vec3(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
                        Vec3 to = new Vec3(
                                originCenter.x - dragon.getX(),
                                originCenter.y - dragon.getY(),
                                originCenter.z - dragon.getZ());
                        dir = new Vec3(to.x, 0.0, to.z);
                        targetY = originCenter.y;

                        if (LOGGER.isDebugEnabled() && (now % 200L) == 0) {
                            LOGGER.debug("[Sky-Islands][dragons][passive] headAvoid steerToFightOrigin uuid={} origin={} delta=({}, {}, {})",
                                    dragon.getStringUUID(),
                                    origin,
                                    Math.round(to.x * 10.0) / 10.0,
                                    Math.round(to.y * 10.0) / 10.0,
                                    Math.round(to.z * 10.0) / 10.0);
                        }
                    } else {
                        dir = new Vec3(s.headingX(), 0.0, s.headingZ());
                        targetY = s.pos().y;
                    }

                    double len = dir.length();
                    if (len > 1.0e-6) {
                        dir = dir.scale(1.0 / len);
                    } else {
                        dir = new Vec3(1, 0, 0);
                    }

                    double dy = (targetY - dragon.getY()) * 0.02;
                    dy = Math.max(-0.15, Math.min(0.15, dy));

                    Vec3 desiredVel = new Vec3(dir.x * desiredSpeed, dy, dir.z * desiredSpeed);
                    newVel = dragon.getDeltaMovement().scale(0.70).add(desiredVel.scale(0.30));

                    double speed = newVel.length();
                    double maxSpeed = desiredSpeed + 0.25;
                    if (speed > maxSpeed) {
                        newVel = newVel.scale(maxSpeed / speed);
                    }

                    dragon.setDeltaMovement( newVel);
                    skyIslands$faceDirection(dragon, newVel);
                    if (!headAvoidActive && LOGGER.isDebugEnabled() && (now % 200L) == 0) {
                        LOGGER.debug("[Sky-Islands][dragons][passive] followHeading uuid={} heading=({}, {}) vel=({}, {}, {})",
                                dragon.getStringUUID(),
                                Math.round(s.headingX() * 100.0) / 100.0,
                                Math.round(s.headingZ() * 100.0) / 100.0,
                                Math.round(newVel.x * 100.0) / 100.0,
                                Math.round(newVel.y * 100.0) / 100.0,
                                Math.round(newVel.z * 100.0) / 100.0);
                    }
                }
            }

            if (newVel == null && LOGGER.isDebugEnabled() && (now % 200L) == 0) {
                LOGGER.debug("[Sky-Islands][dragons][passive] no virtual heading available uuid={}", dragon.getStringUUID());
            }
            return;
        }

        ServerPlayer target = null;
        if (this.skyIslands$provokingPlayerUuid != null && world.getServer() != null) {
            target = world.getServer().getPlayerList().getPlayer(this.skyIslands$provokingPlayerUuid);
            if (target != null && target.level() != world) {
                target = null;
            }
        }

        if (target == null) {
            // No valid target (logged out / different dimension). Keep provoked state but do nothing special.
            if (LOGGER.isDebugEnabled() && (now % 200L) == 0) {
                LOGGER.debug("[Sky-Islands][dragons][combat] no target uuid={} provokingPlayerUuid={}",
                        dragon.getStringUUID(), String.valueOf(this.skyIslands$provokingPlayerUuid));
            }
            return;
        }

        // Keep vanilla pathing anchored to the player.
        dragon.setFightOrigin(target.blockPosition());

        // --- Charge ---
        if (this.skyIslands$chargeTicksRemaining > 0) {
            Vec3 from = new Vec3(dragon.getX(), dragon.getY() + 2.0, dragon.getZ());
            Vec3 to = new Vec3(target.getX(), target.getY() + 1.4, target.getZ());
            Vec3 desiredDir = to.subtract(from);
            double dLen = desiredDir.length();
            if (dLen > 1.0e-6) {
                desiredDir = desiredDir.scale(1.0 / dLen);
            } else {
                desiredDir = new Vec3(1, 0, 0);
            }

            desiredDir = skyIslands$turnLimitedDir(dragon, desiredDir, 0.25);

            double chargeSpeed = 1.10;
            Vec3 desiredVel = desiredDir.scale(chargeSpeed);
            Vec3 newVel = dragon.getDeltaMovement().scale(0.45).add(desiredVel.scale(0.55));

            double speed = newVel.length();
            double maxSpeed = 1.35;
            if (speed > maxSpeed) {
                newVel = newVel.scale(maxSpeed / speed);
            }

            dragon.setDeltaMovement( newVel);
            skyIslands$faceDirection(dragon, newVel);
            this.skyIslands$chargeTicksRemaining--;
            if (LOGGER.isDebugEnabled() && (this.skyIslands$chargeTicksRemaining % 10) == 0) {
                LOGGER.debug("[Sky-Islands][dragons][combat] charge tick uuid={} remaining={} vel=({}, {}, {})",
                        dragon.getStringUUID(),
                        this.skyIslands$chargeTicksRemaining,
                        Math.round(newVel.x * 100.0) / 100.0,
                        Math.round(newVel.y * 100.0) / 100.0,
                        Math.round(newVel.z * 100.0) / 100.0);
            }
            return;
        }

        // Start a charge periodically.
        if (now >= this.skyIslands$nextChargeTick) {
            Vec3 from = new Vec3(dragon.getX(), dragon.getY() + 2.0, dragon.getZ());
            Vec3 to = new Vec3(target.getX(), target.getY() + 1.4, target.getZ());
            Vec3 d = to.subtract(from);
            double dLen = d.length();
            if (dLen > 1.0e-6) {
                d = d.scale(1.0 / dLen);
            } else {
                d = new Vec3(1, 0, 0);
            }

            this.skyIslands$chargeDirX = d.x;
            this.skyIslands$chargeDirY = d.y;
            this.skyIslands$chargeDirZ = d.z;

            this.skyIslands$chargeTicksRemaining = 22;
            this.skyIslands$nextChargeTick = now + 140;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] charge start uuid={} target={} nextChargeTick={}",
                        dragon.getStringUUID(), target.getScoreboardName(), this.skyIslands$nextChargeTick);
            }
            return;
        }

        // --- Breath (dragon fireball) ---
        if (now >= this.skyIslands$nextBreathTick) {
            Vec3 from = new Vec3(dragon.getX(), dragon.getY() + 4.0, dragon.getZ());
            Vec3 to = new Vec3(target.getX(), target.getY() + 1.0, target.getZ());
            Vec3 dir = to.subtract(from);
            double len = dir.length();
            if (len > 1.0e-6) {
                dir = dir.scale(1.0 / len);
            } else {
                dir = new Vec3(1, 0, 0);
            }

            DragonFireball fireball = new DragonFireball(world, dragon, dir);
            fireball.setPos(from.x, from.y, from.z);
            fireball.setYRot(dragon.getYRot());
            fireball.setXRot(dragon.getXRot());
            world.addFreshEntity(fireball);

            this.skyIslands$nextBreathTick = now + 70;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] breath uuid={} target={} nextBreathTick={}",
                        dragon.getStringUUID(), target.getScoreboardName(), this.skyIslands$nextBreathTick);
            }
        }

        // --- Chase steering (stronger) ---
        Vec3 dragonPos = new Vec3(dragon.getX(), dragon.getY(), dragon.getZ());
        Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ());

        double distToTarget = targetPos.distanceTo(dragonPos);

        // When close to the player, don't "home" into them (which can result in hovering directly above
        // and rotating). Instead, pick an overshoot direction and fly THROUGH the player like a swoop.
        if (distToTarget < 30.0 && now >= this.skyIslands$flybyUntilTick) {
            Vec3 baseDir = skyIslands$currentHorizontalDir(dragon);
            if (baseDir.lengthSqr() < 1.0e-6) {
                Vec3 horiz = targetPos.subtract(dragonPos);
                horiz = new Vec3(horiz.x, 0.0, horiz.z);
                double hlen = horiz.length();
                baseDir = hlen > 1.0e-3 ? horiz.scale(1.0 / hlen) : new Vec3(1, 0, 0);
            }

            this.skyIslands$flybyDirX = baseDir.x;
            this.skyIslands$flybyDirZ = baseDir.z;
            this.skyIslands$flybyUntilTick = now + 50;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] flyby start uuid={} untilTick={} dist={}",
                        dragon.getStringUUID(), this.skyIslands$flybyUntilTick, Math.round(distToTarget * 10.0) / 10.0);
            }
        }

        if (this.skyIslands$flybyUntilTick > 0 && now >= this.skyIslands$flybyUntilTick) {
            Vec3 flyH = new Vec3(this.skyIslands$flybyDirX, 0.0, this.skyIslands$flybyDirZ);
            double flyLen = flyH.length();
            if (flyLen > 1.0e-6) {
                flyH = flyH.scale(1.0 / flyLen);
            }
            Vec3 rel = new Vec3(dragonPos.x - targetPos.x, 0.0, dragonPos.z - targetPos.z);
            double along = rel.dot(flyH);
            if (along < 30.0) {
                this.skyIslands$flybyUntilTick = now + 10;
            } else {
                this.skyIslands$flybyUntilTick = 0;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][dragons][combat] flyby end uuid={} along={}",
                            dragon.getStringUUID(), Math.round(along * 10.0) / 10.0);
                }
            }
        }

        Vec3 aim;
        if (now < this.skyIslands$flybyUntilTick) {
            // Recompute a fly-through direction so it keeps tracking the player's current position.
            Vec3 baseDir = targetPos.subtract(dragonPos);
            baseDir = new Vec3(baseDir.x, 0.0, baseDir.z);
            double bLen = baseDir.length();
            if (bLen > 1.0e-6) {
                baseDir = baseDir.scale(1.0 / bLen);
            } else {
                baseDir = new Vec3(this.skyIslands$flybyDirX, 0.0, this.skyIslands$flybyDirZ);
                double fLen = baseDir.length();
                baseDir = fLen > 1.0e-6 ? baseDir.scale(1.0 / fLen) : new Vec3(1, 0, 0);
            }

            this.skyIslands$flybyDirX = baseDir.x;
            this.skyIslands$flybyDirZ = baseDir.z;

            double overshoot = 70.0;
            aim = new Vec3(
                    targetPos.x + baseDir.x * overshoot,
                    target.getY() + 1.4,
                    targetPos.z + baseDir.z * overshoot
            );
        } else {
            // Farther away: lead the target a bit so it feels like pursuit, not perfect tracking.
            Vec3 pv = target.getDeltaMovement();
            double leadSeconds = 0.6;
            aim = new Vec3(
                    targetPos.x + pv.x * (leadSeconds * 20.0),
                    target.getY() + 1.8,
                    targetPos.z + pv.z * (leadSeconds * 20.0)
            );
        }

        Vec3 dir = aim.subtract(dragonPos);
        double len = dir.length();
        if (len > 1.0e-6) {
            dir = dir.scale(1.0 / len);
        } else {
            dir = new Vec3(1, 0, 0);
        }

        dir = skyIslands$turnLimitedDir(dragon, dir, 0.2125);

        // More aggressive chase that still keeps reasonable caps.
        double baseChaseSpeed = 0.75;
        var cfg = EnderDragonManager.getConfig();
        if (cfg != null) {
            baseChaseSpeed = Math.max(0.35, Math.min(1.4, cfg.virtualSpeedBlocksPerTick + 0.15));
        }

        double desiredSpeed = baseChaseSpeed + Math.min(0.35, distToTarget / 180.0);
        Vec3 desiredVel = dir.scale(desiredSpeed);

        Vec3 newVel = dragon.getDeltaMovement().scale(0.80).add(desiredVel.scale(0.20));
        double speed = newVel.length();
        double maxSpeed = baseChaseSpeed + 0.55;
        if (speed > maxSpeed) {
            newVel = newVel.scale(maxSpeed / speed);
        }

        dragon.setDeltaMovement( newVel);
        skyIslands$faceDirection(dragon, newVel);

        if (LOGGER.isDebugEnabled() && (now % 200L) == 0) {
            LOGGER.debug("[Sky-Islands][dragons][combat] chase uuid={} target={} dist={} vel=({}, {}, {})",
                    dragon.getStringUUID(),
                    target.getScoreboardName(),
                    Math.round(distToTarget * 10.0) / 10.0,
                    Math.round(newVel.x * 100.0) / 100.0,
                    Math.round(newVel.y * 100.0) / 100.0,
                    Math.round(newVel.z * 100.0) / 100.0);
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"))
    private void skyIslands$onUpdatePostDeath(CallbackInfo ci) {
        if (this.skyIslands$deathHandled) {
            return;
        }
        this.skyIslands$deathHandled = true;
        if (LOGGER.isDebugEnabled()) {
            EnderDragon dragon = (EnderDragon) (Object) this;
            LOGGER.debug("[Sky-Islands][dragons] tickDeath uuid={} managed={}", dragon.getStringUUID(), EnderDragonManager.isManaged(dragon));
        }
        EnderDragonManager.onManagedDragonDeath((EnderDragon) (Object) this);
    }
}
