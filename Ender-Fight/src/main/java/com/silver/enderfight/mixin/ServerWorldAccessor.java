package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.duck.ServerWorldDuck;
import com.silver.enderfight.util.WorldSeedOverrides;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to override ServerWorld seed at runtime for custom End dimensions.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldAccessor implements ServerWorldDuck {
    @Unique
    private Long enderfight$overriddenSeed = null;
    
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void overrideSeed(CallbackInfoReturnable<Long> cir) {
        // First check instance-level override
        if (enderfight$overriddenSeed != null) {
            EnderFightMod.LOGGER.debug("Returning instance-level seed override: {}", enderfight$overriddenSeed);
            cir.setReturnValue(enderfight$overriddenSeed);
            return;
        }
        
        // Fallback to registry-based override
        ServerWorld self = (ServerWorld)(Object)this;
        Long overrideSeed = WorldSeedOverrides.getSeedOverride(self.getRegistryKey());
        if (overrideSeed != null) {
            EnderFightMod.LOGGER.debug("Returning registry-level seed override for {}: {}", 
                self.getRegistryKey().getValue(), overrideSeed);
            cir.setReturnValue(overrideSeed);
        }
    }
    
    @Override
    public void enderfight$setSeed(long seed) {
        this.enderfight$overriddenSeed = seed;
    }
    
    @Override
    public Long enderfight$getOverriddenSeed() {
        return this.enderfight$overriddenSeed;
    }
}
