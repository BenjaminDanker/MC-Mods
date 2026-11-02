package com.silver.elementchoice.mixin;

import com.silver.elementchoice.ElementChoiceMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports an advancement the moment it becomes complete, which Fabric API has no event for. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;

    @Shadow public abstract net.minecraft.advancements.AdvancementProgress getOrStartProgress(AdvancementHolder holder);

    @Unique private boolean elementchoice$wasDone;

    @Inject(method = "award", at = @At("HEAD"))
    private void elementchoice$before(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        elementchoice$wasDone = getOrStartProgress(holder).isDone();
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void elementchoice$after(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !elementchoice$wasDone && getOrStartProgress(holder).isDone()) {
            ElementChoiceMod.onAdvancementCompleted(player, holder);
        }
    }
}
