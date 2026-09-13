package com.silver.spawnprotect.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Suppresses OpenPAC's protected-action chat while retaining its denial result. */
@Mixin(targets = "xaero.pac.common.server.claims.protection.ChunkProtection", remap = false)
public abstract class OpenPacFeedbackMixin {
    @Redirect(
        method = {"onBlockInteraction", "onEntityInteraction"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"
        ),
        require = 1
    )
    private void spawnprotect$suppressOpenPacInteractionFeedback(ServerPlayer player, Component message) {
        // OpenPAC has already denied the action.
    }
}
