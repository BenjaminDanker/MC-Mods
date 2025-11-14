package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.portal.PortalInterceptor;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    
    @Shadow
    private World world;
    
    @Shadow
    public abstract BlockPos getBlockPos();
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void checkEndPortalCollision(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        // Only check server-side players
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        
        if (!(this.world instanceof ServerWorld serverWorld)) {
            return;
        }
        
        // Check if this world is one of the managed End dimensions (vanilla or custom)
        if (!PortalInterceptor.isManagedEndDimension(serverWorld.getRegistryKey())) {
            return;
        }
        
        BlockPos pos = this.getBlockPos();
        BlockState state = serverWorld.getBlockState(pos);

        boolean inPortalBlock = state.getBlock() instanceof net.minecraft.block.EndPortalBlock;
        if (!inPortalBlock) {
            BlockState below = serverWorld.getBlockState(pos.down());
            inPortalBlock = below.getBlock() instanceof net.minecraft.block.EndPortalBlock;
        }

        // Check if standing in or directly above an End portal block
        if (inPortalBlock) {
            EnderFightMod.LOGGER.info("End portal collision detected for player {} at {}", player.getName().getString(), pos);
            
            // Try to intercept and redirect
            if (PortalInterceptor.tryInterceptEndPortal(player)) {
                EnderFightMod.LOGGER.info("Portal interception successful, redirecting {} away from End portal", player.getName().getString());
            } else {
                EnderFightMod.LOGGER.debug("Portal interception did not trigger for {}", player.getName().getString());
            }
        }
    }
}
