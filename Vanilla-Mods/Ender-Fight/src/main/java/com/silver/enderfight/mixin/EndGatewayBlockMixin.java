package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.portal.PortalInterceptor;
import com.silver.enderfight.reset.EndResetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps End gateway teleports inside the managed End dimension so players do not get kicked over
 * to other servers. Relies on vanilla helper methods to preserve the usual behaviour.
 */
@Mixin(EndGatewayBlock.class)
public abstract class EndGatewayBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void enderfight$redirectGatewayTeleport(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier collisionHandler, boolean moved, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        ServerPlayer player = entity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        ThrownEnderpearl pearl = entity instanceof ThrownEnderpearl thrownPearl ? thrownPearl : null;
        ServerPlayer pearlOwner = pearl != null && pearl.getOwner() instanceof ServerPlayer serverPlayer
            ? serverPlayer
            : null;
        if (player == null && pearlOwner == null) {
            return;
        }
        if (!entity.canUsePortal(false)) {
            return;
        }
        if (!PortalInterceptor.isManagedEndDimension(serverWorld.dimension())) {
            return;
        }

        BlockEntity blockEntity = serverWorld.getBlockEntity(pos);
        if (!(blockEntity instanceof TheEndGatewayBlockEntity gateway)) {
            return;
        }
        if (gateway.isCoolingDown()) {
            return;
        }

        MinecraftServer server = serverWorld.getServer();
        EndResetManager manager = EnderFightMod.getEndResetManager();
        if (server == null || manager == null) {
            return;
        }

        ServerLevel destinationWorld = manager.getActiveEndWorld(server);
        if (destinationWorld == null) {
            destinationWorld = serverWorld;
        }

        Vec3 exitPos;
        Vec3 velocity = entity.getDeltaMovement();
        float targetYaw = entity.getYRot();
        float targetPitch = entity.getXRot();

        boolean sendToSpawnPlatform = shouldSendToSpawn(manager, destinationWorld, pos);
        if (sendToSpawnPlatform) {
            // Force returning gateways in managed End dimensions to land on the central platform instead of looping back to the same island.
            manager.ensureEndSpawnPlatform(destinationWorld);
            exitPos = manager.getEndSpawnLocation();
            velocity = Vec3.ZERO;
            targetYaw = manager.getEndSpawnYaw();
            targetPitch = 0.0F;
            if (player != null) {
                PortalInterceptor.suppressNextRedirect(player);
            } else {
                PortalInterceptor.suppressNextRedirect(pearlOwner);
            }
        } else {
            exitPos = gateway.getPortalPosition(destinationWorld, pos);
            if (exitPos == null) {
                BlockPos portalBase = EndGatewayBlockEntityInvoker.enderfight$setupExitPortalLocation(destinationWorld, pos);
                if (portalBase == null) {
                    EnderFightMod.LOGGER.warn("Gateway at {} failed to locate exit site in {}", pos, destinationWorld.dimension().identifier());
                    return;
                }
                BlockPos elevated = portalBase.above(10);
                EndGatewayBlockEntityInvoker.enderfight$createPortal(destinationWorld, elevated, EndGatewayConfiguration.knownExit(portalBase, false));
                gateway.setExitPosition(elevated, false);
                exitPos = gateway.getPortalPosition(destinationWorld, pos);
            }
        }

        if (exitPos == null) {
            EnderFightMod.LOGGER.warn("Gateway at {} still missing exit portal after creation attempt", pos);
            return;
        }

        TeleportTransition target = new TeleportTransition(
            destinationWorld,
            exitPos,
            velocity,
            targetYaw,
            targetPitch,
            TeleportTransition.PLACE_PORTAL_TICKET
        );

        entity.teleport(target);
        String actorName = player != null ? player.getName().getString() : pearlOwner.getName().getString() + "'s ender pearl";
        EnderFightMod.LOGGER.info("Gateway at {} teleported {} to {}", pos, actorName, destinationWorld.dimension().identifier());
        TheEndGatewayBlockEntity.triggerCooldown(serverWorld, pos, state, gateway);
        ci.cancel();
    }

    private static boolean shouldSendToSpawn(EndResetManager manager, ServerLevel destinationWorld, BlockPos gatewayPos) {
        if (!PortalInterceptor.isManagedEndDimension(destinationWorld.dimension()) || Level.END.equals(destinationWorld.dimension())) {
            return false;
        }

        BlockPos spawnBase = manager.getEndSpawnPlatformBase();
        if (spawnBase == null) {
            return false;
        }

        double distanceSq = gatewayPos.distSqr(spawnBase);
        // Vanilla outer gateways spawn roughly 1000+ blocks away; anything beyond 256 blocks from the managed spawn is treated as a return portal.
        return distanceSq > 256D * 256D;
    }
}
