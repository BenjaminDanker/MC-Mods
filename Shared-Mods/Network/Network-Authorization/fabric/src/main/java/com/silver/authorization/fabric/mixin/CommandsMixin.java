package com.silver.authorization.fabric.mixin;

import com.silver.authorization.fabric.CommandPolicyRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
abstract class CommandsMixin {
    @Shadow private com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void networkAuthorization$indexCommandTree(Commands.CommandSelection selection,
                                                        net.minecraft.commands.CommandBuildContext context,
                                                        CallbackInfo ci) {
        CommandPolicyRuntime.indexDispatcher(dispatcher.getRoot());
    }
}
