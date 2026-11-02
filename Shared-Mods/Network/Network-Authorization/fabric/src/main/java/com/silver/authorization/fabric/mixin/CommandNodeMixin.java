package com.silver.authorization.fabric.mixin;

import com.mojang.brigadier.tree.CommandNode;
import com.silver.authorization.fabric.CommandPolicyRuntime;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CommandNode.class, remap = false)
abstract class CommandNodeMixin<S> {
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void networkAuthorization$applyPolicy(Object source, CallbackInfoReturnable<Boolean> cir) {
        @SuppressWarnings("unchecked")
        CommandNode<?> node = (CommandNode<?>) (Object) this;
        Optional<Boolean> visible = CommandPolicyRuntime.visibleOverride(node, source);
        visible.ifPresent(cir::setReturnValue);
    }
}
