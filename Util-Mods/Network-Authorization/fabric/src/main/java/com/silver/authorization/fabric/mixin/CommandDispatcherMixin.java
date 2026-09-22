package com.silver.authorization.fabric.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.silver.authorization.fabric.CommandPolicyRuntime;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enforces policy for ordinary dispatch and redirected commands (for example `/execute run`). */
@Mixin(value = CommandDispatcher.class, remap = false)
abstract class CommandDispatcherMixin<S> {
    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;)I", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void networkAuthorization$enforceEveryDispatch(ParseResults<S> parsed,
                                                             CallbackInfoReturnable<Integer> cir) {
        if (!(parsed.getContext().getSource() instanceof CommandSourceStack)) return;
        @SuppressWarnings("unchecked")
        ParseResults<CommandSourceStack> typed = (ParseResults<CommandSourceStack>) (ParseResults<?>) parsed;
        if (!CommandPolicyRuntime.beforeExecution(typed)) cir.setReturnValue(0);
    }
}
