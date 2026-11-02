package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.portal.PortalInterceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void enderfight$blockVanillaEndPortalTeleport(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier collisionHandler, boolean moved, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!entity.canUsePortal(false)) {
            return;
        }
        if (!PortalInterceptor.isManagedEndDimension(serverWorld.dimension())) {
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
