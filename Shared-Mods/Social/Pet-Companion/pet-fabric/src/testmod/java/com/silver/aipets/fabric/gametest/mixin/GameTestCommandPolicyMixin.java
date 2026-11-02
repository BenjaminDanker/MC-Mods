package com.silver.aipets.fabric.gametest.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import com.silver.authorization.fabric.CommandPolicyRuntime;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Testmod-only: exercise PetPermissions without a signed Velocity snapshot in GameTests. */
@Mixin(CommandPolicyRuntime.class)
public abstract class GameTestCommandPolicyMixin {
    @Inject(method = "visibleOverride", at = @At("HEAD"), cancellable = true)
    private static void aipets$useCommandRequirementsInTests(
            CommandNode<?> node, Object source,
            CallbackInfoReturnable<Optional<Boolean>> result) {
        result.setReturnValue(Optional.empty());
    }

    @Inject(method = "beforeExecution", at = @At("HEAD"), cancellable = true)
    private static void aipets$usePetPermissionsInTests(
            ParseResults<CommandSourceStack> parsed, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(true);
    }
}
