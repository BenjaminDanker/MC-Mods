package com.silver.skyislands.enderdragons.mixins;
import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers placement, commands, explosions and replacement, not only player breaks. */
@Mixin(LevelChunk.class)
public abstract class DragonHeadBlockChangeMixin {
    @Inject(method="setBlockState", at=@At("RETURN"))
    private void skyislands$headChanged(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        BlockState old=cir.getReturnValue();
        if (old == null) return;
        boolean before=old.is(Blocks.DRAGON_HEAD)||old.is(Blocks.DRAGON_WALL_HEAD);
        boolean after=state.is(Blocks.DRAGON_HEAD)||state.is(Blocks.DRAGON_WALL_HEAD);
        if (before == after) return;
        if (((LevelChunk)(Object)this).getLevel() instanceof ServerLevel world)
            EnderDragonManager.onDragonHeadChanged(world,pos,old,after);
    }
}
