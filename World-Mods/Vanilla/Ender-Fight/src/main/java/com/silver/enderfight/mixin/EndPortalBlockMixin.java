package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.portal.PortalInterceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts End portal frame block teleportation to redirect players to configured servers.
 * Uses Entity.tick injection to detect when player enters End portal frame.
 * Mirrors the approach used by CustomPortalAPI's EntityPortalDetectionMixin.
 */
@Mixin(Entity.class)
public abstract class EndPortalBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void checkEndPortalCollision(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        // Only check server-side players
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        
        if (!(entity.level() instanceof ServerLevel serverWorld)) {
            return;
        }
        
        // Check if this world is one of the managed End dimensions (vanilla or custom)
        if (!PortalInterceptor.isManagedEndDimension(serverWorld.dimension())) {
            return;
        }
        
        BlockPos pos = entity.blockPosition();
        BlockState state = serverWorld.getBlockState(pos);

        boolean inPortalBlock = state.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock;
        if (!inPortalBlock) {
            BlockState below = serverWorld.getBlockState(pos.below());
            inPortalBlock = below.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock;
        }

        boolean shouldAttemptRedirect = PortalInterceptor.onEndPortalPresenceTick(player, inPortalBlock);
        if (!shouldAttemptRedirect) {
            return;
        }

        EnderFightMod.LOGGER.info("End portal entry detected for player {} at {}", player.getName().getString(), pos);

        if (PortalInterceptor.tryInterceptEndPortal(player)) {
            EnderFightMod.LOGGER.info("Portal interception successful, redirecting {} away from End portal", player.getName().getString());
        } else {
            EnderFightMod.LOGGER.debug("Portal interception did not trigger for {}", player.getName().getString());
        }
    }
}
