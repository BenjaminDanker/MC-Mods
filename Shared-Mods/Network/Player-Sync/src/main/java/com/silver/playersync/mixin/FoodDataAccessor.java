package com.silver.playersync.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The two food values vanilla keeps private (exhaustion and the regeneration timer), so they travel too. */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
    @Accessor("exhaustionLevel")
    float playersync$getExhaustion();

    @Accessor("exhaustionLevel")
    void playersync$setExhaustion(float exhaustion);

    @Accessor("tickTimer")
    int playersync$getTickTimer();

    @Accessor("tickTimer")
    void playersync$setTickTimer(int ticks);
}
