package com.silver.spawnprotect.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/** Suppresses only OpenPAC's repetitive protected-action system-chat text. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSystemMessageMixin {
    @Inject(method = "sendSystemMessage", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$suppressOpenPacProtectedActionSpam(Component message, CallbackInfo ci) {
        String text = message == null ? "" : message.getString().toLowerCase(Locale.ROOT);
        if (text.contains("you are not allowed to interact")
            || text.contains("you are not allowed to use this item")
            || text.contains("you are not allowed to apply this item")
            || text.contains("not allowed to land on")
            || text.contains("interaction with this block is disabled")
            || text.contains("interaction with this entity is disabled")) {
            ci.cancel();
        }
    }
}
