package com.silver.playersync.mixin;

import com.silver.playersync.PlayerSyncMod;
import com.silver.playersync.SharedAdvancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notices the moment a player completes an advancement that carries a reward (see AdvancementRewards), and tells
 * SharedAdvancements about every criterion earned or revoked.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Unique
    private boolean playersync$doneBefore;

    @Inject(method = "award", at = @At("HEAD"))
    private void playersync$before(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        playersync$doneBefore = PlayerSyncMod.rewarded(advancement.id()) && getOrStartProgress(advancement).isDone();
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void playersync$after(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            SharedAdvancements.earned(player, advancement, criterion);
        }
        if (cir.getReturnValueZ() && !playersync$doneBefore && PlayerSyncMod.rewarded(advancement.id())
                && getOrStartProgress(advancement).isDone()) {
            PlayerSyncMod.advancementCompleted(player, advancement.id());
        }
    }

    @Inject(method = "revoke", at = @At("RETURN"))
    private void playersync$revoked(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            SharedAdvancements.revoked(player, advancement, criterion);
        }
    }
}
