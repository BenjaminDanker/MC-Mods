package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.dragon.DragonBreathModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Purges special dragon breath bottles when they are dropped in the End so players cannot stash them on the ground.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void enderfight$deleteSpecialDragonBreath(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        Entity entity = (Entity) (Object) this;
        World world = entity.getEntityWorld();
        if (world == null || world.isClient()) {
            return;
        }
        ItemStack stack = self.getStack();
        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        if (!DragonBreathModifier.isSpecialDragonBreath(stack)) {
            return;
        }

        if (!isEndDimension(world)) {
            EnderFightMod.LOGGER.info(
                "Special dragon breath drop detected outside End dimension type; skipping removal (world={})",
                world.getRegistryKey().getValue());
            return;
        }

        EnderFightMod.LOGGER.info("Removing dropped special dragon breath immediately at {} in world {}", pos,
            world.getRegistryKey().getValue());
        self.discard();
    }

    private static boolean isEndDimension(World world) {
        return world != null && world.getDimensionEntry().matchesKey(DimensionTypes.THE_END);
    }
}
