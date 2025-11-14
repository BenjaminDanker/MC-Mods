package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.portal.PortalInterceptor;
import com.silver.enderfight.reset.EndResetManager;
import com.silver.enderfight.mixin.EndGatewayBlockEntityInvoker;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndGatewayBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndGatewayFeatureConfig;
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

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void enderfight$redirectGatewayTeleport(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!entity.canUsePortals(false)) {
            return;
        }
        if (!PortalInterceptor.isManagedEndDimension(serverWorld.getRegistryKey())) {
            return;
        }

        BlockEntity blockEntity = serverWorld.getBlockEntity(pos);
        if (!(blockEntity instanceof EndGatewayBlockEntity gateway)) {
            return;
        }
        if (gateway.needsCooldownBeforeTeleporting()) {
            return;
        }

        MinecraftServer server = serverWorld.getServer();
        EndResetManager manager = EnderFightMod.getEndResetManager();
        if (server == null || manager == null) {
            return;
        }

        ServerWorld destinationWorld = manager.getActiveEndWorld(server);
        if (destinationWorld == null) {
            destinationWorld = serverWorld;
        }

        Vec3d exitPos = gateway.getOrCreateExitPortalPos(destinationWorld, pos);
        if (exitPos == null) {
            BlockPos portalBase = EndGatewayBlockEntityInvoker.enderfight$setupExitPortalLocation(destinationWorld, pos);
            if (portalBase == null) {
                EnderFightMod.LOGGER.warn("Gateway at {} failed to locate exit site in {}", pos, destinationWorld.getRegistryKey().getValue());
                return;
            }
            BlockPos elevated = portalBase.up(10);
            EndGatewayBlockEntityInvoker.enderfight$createPortal(destinationWorld, elevated, EndGatewayFeatureConfig.createConfig(portalBase, false));
            gateway.setExitPortalPos(elevated, false);
            exitPos = gateway.getOrCreateExitPortalPos(destinationWorld, pos);
        }

        if (exitPos == null) {
            EnderFightMod.LOGGER.warn("Gateway at {} still missing exit portal after creation attempt", pos);
            return;
        }

        TeleportTarget target = new TeleportTarget(
            destinationWorld,
            exitPos,
            entity.getVelocity(),
            entity.getYaw(),
            entity.getPitch(),
            TeleportTarget.ADD_PORTAL_CHUNK_TICKET
        );

        entity.teleportTo(target);
        EnderFightMod.LOGGER.info("Gateway at {} teleported {} to {}", pos, player.getName().getString(), destinationWorld.getRegistryKey().getValue());
        EndGatewayBlockEntity.startTeleportCooldown(serverWorld, pos, state, gateway);
        ci.cancel();
    }
}
