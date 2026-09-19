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
    @org.spongepowered.asm.mixin.Shadow private boolean inWall;
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(EnderDragonEntityMixin.class);

    @Unique
    private boolean skyIslands$provokedByPlayer;

    @Unique
    private boolean skyIslands$deathHandled;

    @Unique
    private UUID skyIslands$provokingPlayerUuid;

    @Unique
    private final com.silver.skyislands.enderdragons.DragonCombatController skyIslands$combat = new com.silver.skyislands.enderdragons.DragonCombatController();

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
    public void skyIslands$stopCombat() {
        skyIslands$provokedByPlayer=false;
        skyIslands$provokingPlayerUuid=null;
        skyIslands$combat.interrupt();
    }

    @Override
    public boolean skyIslands$isProvoked() {
        return this.skyIslands$provokedByPlayer;
    }

    @Inject(method="knockback", at=@At("HEAD"), cancellable=true)
    private void skyIslands$ignoreHitKnockback(double power,double x,double z,DamageSource source,float damage,CallbackInfo ci) {
        // Vanilla hurt handling still applies damage and hurt animation, but cannot kick this
        // large flying entity's velocity away from its committed path on every arrow impact.
        if (EnderDragonManager.isManaged((EnderDragon)(Object)this)) ci.cancel();
    }

    @Inject(method="knockBack(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at=@At("HEAD"), cancellable=true)
    private void skyIslands$gateWingContact(ServerLevel world,java.util.List<Entity> entities,CallbackInfo ci) {
        if (EnderDragonManager.isManaged((EnderDragon)(Object)this) && !skyIslands$combat.contactAttack()) ci.cancel();
    }

    @Inject(method="hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at=@At("HEAD"), cancellable=true)
    private void skyIslands$gateHeadContact(ServerLevel world,java.util.List<Entity> entities,CallbackInfo ci) {
        if (EnderDragonManager.isManaged((EnderDragon)(Object)this) && !skyIslands$combat.contactAttack()) ci.cancel();
    }

    @Unique
    private void skyIslands$maybeMarkProvoked(ServerLevel world, DamageSource source) {
        if (!EnderDragonManager.isManaged((EnderDragon)(Object)this)) return;
        skyIslands$combat.observeDamage((EnderDragon)(Object)this, source);
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

        if (!(player instanceof ServerPlayer) || !player.isAlive() || player.isSpectator() || player.level()!=world) {
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

        this.skyIslands$combat.engage(dragon, (ServerPlayer) player);

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

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method="hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at=@At("HEAD"), argsOnly=true, ordinal=0)
    private float skyIslands$exhaustedHeadDamage(float amount, ServerLevel world,
            net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part, DamageSource source, float originalAmount) {
        EnderDragon dragon=(EnderDragon)(Object)this;
        return EnderDragonManager.isManaged(dragon) && part==dragon.head
                ? skyIslands$combat.headDamage(amount) : amount;
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="aiStep", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;doServerTick(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void skyIslands$managedPhaseTick(net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance phase,
            ServerLevel world, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        EnderDragon dragon=(EnderDragon)(Object)this;
        if (!EnderDragonManager.isManaged(dragon) || dragon.isDeadOrDying()
                || phase.getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING)
            original.call(phase,world);
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="aiStep", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getFlyTargetLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 skyIslands$managedFlightTarget(Vec3 vanilla) {
        EnderDragon dragon=(EnderDragon)(Object)this;
        if (!EnderDragonManager.isManaged(dragon) || dragon.isDeadOrDying()
                || dragon.getPhaseManager().getCurrentPhase().getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING) return vanilla;
        // Vanilla aiStep puts ALL steering, acceleration, movement and damping inside
        // its non-null target branch. Move exactly once here, then skip that branch.
        // The remainder still updates parts, contact damage, walls and flight history.
        dragon.move(net.minecraft.world.entity.MoverType.SELF,
                inWall ? dragon.getDeltaMovement().scale(.8) : dragon.getDeltaMovement());
        return null;
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void skyIslands$customProvokedCombat(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (!(dragon.level() instanceof ServerLevel world)) {
            return;
        }
        if (!EnderDragonManager.isManaged(dragon) || dragon.isDeadOrDying() || dragon.isNoAi()
                || dragon.getPhaseManager().getCurrentPhase().getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING) {
            return;
        }
        boolean provoked = this.skyIslands$provokedByPlayer;

        // Disengage rule: once very low HP, return to roaming and never re-aggro.
        if (provoked && dragon.getHealth() <= (dragon.getMaxHealth() / 8.0f)) {
            this.skyIslands$provokedByPlayer = false;
            this.skyIslands$provokingPlayerUuid = null;
            this.skyIslands$combat.interrupt();
            provoked = false;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][combat] disengage uuid={} lowHP hp={} max={}",
                        dragon.getStringUUID(), dragon.getHealth(), dragon.getMaxHealth());
            }
        }

        long now = world.getGameTime();
        if (EnderDragonManager.applyHeadAvoidance(dragon)) {
            this.skyIslands$combat.interrupt();
            Vec3 away=Vec3.atCenterOf(dragon.getFightOrigin()).subtract(dragon.position());
            if (away.lengthSqr()>1e-6) {
                Vec3 velocity=away.normalize().scale(.85);
                dragon.setDeltaMovement(velocity);
                skyIslands$faceDirection(dragon,velocity);
            }
            return;
        }

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

        target=this.skyIslands$combat.selectTarget(dragon,target);
        if (target == null || !com.silver.skyislands.enderdragons.DragonCombatTargetPolicy.retain(
                target.isAlive(), target.gameMode.getGameModeForPlayer(), target.level()==world, dragon.distanceToSqr(target))) {
            this.skyIslands$provokedByPlayer=false;
            this.skyIslands$provokingPlayerUuid=null;
            this.skyIslands$combat.interrupt();
            return;
        }
        this.skyIslands$combat.tick(dragon,target);
        skyIslands$faceDirection(dragon,dragon.getDeltaMovement());
    }

    @Inject(method = "tickDeath", at = @At("HEAD"))
    private void skyIslands$onUpdatePostDeath(CallbackInfo ci) {
        if (this.skyIslands$deathHandled) {
            return;
        }
        this.skyIslands$deathHandled = true;
        this.skyIslands$combat.interrupt();
        if (LOGGER.isDebugEnabled()) {
            EnderDragon dragon = (EnderDragon) (Object) this;
            LOGGER.debug("[Sky-Islands][dragons] tickDeath uuid={} managed={}", dragon.getStringUUID(), EnderDragonManager.isManaged(dragon));
        }
        EnderDragonManager.onManagedDragonDeath((EnderDragon) (Object) this);
    }
}
