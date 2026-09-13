package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$preventInventoryThrow(
        int slotIndex,
        int button,
        ContainerInput actionType,
        Player player,
        CallbackInfo ci
    ) {
        if (actionType != ContainerInput.THROW) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (SpawnProtectionManager.INSTANCE.shouldBlockDrop(serverPlayer)) {
            ci.cancel();
        }
    }
}
