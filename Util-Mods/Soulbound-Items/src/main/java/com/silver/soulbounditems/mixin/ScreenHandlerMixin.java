package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow
    public abstract ItemStack getCursorStack();

    @Shadow
    public abstract void setCursorStack(ItemStack stack);

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void soulbounditems$handleDeliberateOverflowTransfer(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (!SoulboundRules.isAtOrOverLimit(serverPlayer)) {
            return;
        }

        if (!isDeliberateOverflowTransfer(handler, serverPlayer, slotIndex, button, actionType)) {
            return;
        }

        if (tryReturnCursorToContainer(handler, serverPlayer)) {
            SoulboundRules.applyOverflowFlatDamage(serverPlayer);
            ci.cancel();
            return;
        }

        SoulboundRules.applyOverflowFlatDamage(serverPlayer);
        ci.cancel();
    }

    private boolean isDeliberateOverflowTransfer(ScreenHandler handler, ServerPlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
            Slot slot = handler.slots.get(slotIndex);
            boolean sourceIsContainerSlot = !isPlayerInventorySlot(slot, player);
            if (sourceIsContainerSlot && SoulboundRules.isSoulboundItem(slot.getStack())) {
                if (actionType == SlotActionType.QUICK_MOVE
                    || actionType == SlotActionType.SWAP
                    || actionType == SlotActionType.PICKUP
                    || actionType == SlotActionType.PICKUP_ALL
                    || actionType == SlotActionType.QUICK_CRAFT) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryReturnCursorToContainer(ScreenHandler handler, ServerPlayerEntity player) {
        ItemStack cursor = getCursorStack();
        if (!SoulboundRules.isSoulboundItem(cursor)) {
            return false;
        }

        for (Slot slot : handler.slots) {
            if (isPlayerInventorySlot(slot, player)) {
                continue;
            }
            if (!slot.canInsert(cursor)) {
                continue;
            }
            ItemStack existing = slot.getStack();
            if (existing.isEmpty()) {
                slot.setStack(cursor.copy());
                setCursorStack(ItemStack.EMPTY);
                return true;
            }
        }

        return false;
    }

    private boolean isPlayerInventorySlot(Slot slot, ServerPlayerEntity player) {
        return slot.inventory instanceof PlayerInventory && slot.inventory == player.getInventory();
    }
}
