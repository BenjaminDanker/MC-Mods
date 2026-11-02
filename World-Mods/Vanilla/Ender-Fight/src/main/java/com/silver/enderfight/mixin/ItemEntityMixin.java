package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.dragon.DragonBreathModifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.Vec3;
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
        Level world = entity.level();
        if (world == null || world.isClientSide()) {
            return;
        }
        ItemStack stack = self.getItem();
        Vec3 pos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        if (!DragonBreathModifier.isSpecialDragonBreath(stack)) {
            return;
        }

        if (!isEndDimension(world)) {
            EnderFightMod.LOGGER.info(
                "Special dragon breath drop detected outside End dimension type; skipping removal (world={})",
                world.dimension().identifier());
            return;
        }

        EnderFightMod.LOGGER.info("Removing dropped special dragon breath immediately at {} in world {}", pos,
            world.dimension().identifier());
        self.discard();
    }

    private static boolean isEndDimension(Level world) {
        return world != null && world.dimensionTypeRegistration().is(BuiltinDimensionTypes.END);
    }
}
