package com.silver.portals.mixin;

import com.silver.portals.Portals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player standing in a portal block starts vanilla's portal timer, which then asks the block where to go. */
@Mixin(Entity.class)
public abstract class StandInPortalMixin {
    @Shadow
    private Level level;

    @Shadow
    public abstract BlockPos blockPosition();

    @Shadow
    public abstract boolean canUsePortal(boolean ignoreCreative);

    @Inject(method = "tick", at = @At("HEAD"))
    private void portals$enterPortal(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof ServerPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = blockPosition();
        BlockState state = serverLevel.getBlockState(pos);
        if (Portals.isPortal(state) && canUsePortal(false)) {
            self.setAsInsidePortal((Portal) state.getBlock(), pos);
        }
    }
}
