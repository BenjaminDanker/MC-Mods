package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.portal.PortalInterceptor;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla End portal collision handling when Ender-Fight is responsible for redirecting End exits.
 * This prevents login-inside-portal cases from being auto-teleported (custom End -> vanilla End) before
 * the player can step out and re-enter.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockCollisionMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void enderfight$blockVanillaEndPortalTeleport(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler collisionHandler, boolean moved, CallbackInfo ci) {
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
        if (!PortalInterceptor.isPortalRedirectEnabled()) {
            return;
        }

        // While exit is required (login inside portal), always block vanilla so the player can step out.
        if (PortalInterceptor.isEndPortalExitRequired(player)) {
            EnderFightMod.LOGGER.debug("Blocking vanilla End portal collision for {} at {} (exit required)", player.getName().getString(), pos);
            ci.cancel();
            return;
        }

        // When redirecting is enabled in managed End dimensions, Ender-Fight handles End exits.
        // Prevent vanilla from teleporting the player (which can otherwise cause world-change races).
        ci.cancel();
    }
}
