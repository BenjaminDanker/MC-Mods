package com.silver.villagerinterface.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Cutscene speech bubbles change their text as it is "typed". */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void villagerinterface$setText(Component text);
}
