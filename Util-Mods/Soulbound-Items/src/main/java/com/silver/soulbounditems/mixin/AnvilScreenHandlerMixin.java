package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    private static final String NAME_PREFIX_SOULBOUND = "Soulbound ";

    @ModifyVariable(method = "setNewItemName", at = @At("HEAD"), argsOnly = true)
    private String soulbounditems$stripSoulboundFromRenameInput(String name) {
        return stripSoulboundPrefix(name);
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void soulbounditems$enforceSoulboundPrefixInAnvilOutput(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;

        Slot inputSlot = handler.getSlot(0);
        if (inputSlot == null) {
            return;
        }

        ItemStack input = inputSlot.getStack();
        if (!SoulboundRules.isSoulboundItem(input)) {
            return;
        }

        Slot outputSlot = handler.getSlot(2);
        if (outputSlot == null) {
            return;
        }

        ItemStack output = outputSlot.getStack();
        if (output == null || output.isEmpty()) {
            return;
        }

        String currentOutputName = output.getName().getString();
        output.set(DataComponentTypes.CUSTOM_NAME, Text.literal(ensureSoulboundPrefix(currentOutputName)));
    }

    private static String stripSoulboundPrefix(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        if (trimmed.regionMatches(true, 0, NAME_PREFIX_SOULBOUND, 0, NAME_PREFIX_SOULBOUND.length())) {
            return trimmed.substring(NAME_PREFIX_SOULBOUND.length()).trim();
        }

        return trimmed;
    }

    private static String ensureSoulboundPrefix(String raw) {
        String safe = raw == null ? "" : raw.trim();
        if (safe.isEmpty()) {
            return NAME_PREFIX_SOULBOUND.trim();
        }
        if (safe.regionMatches(true, 0, NAME_PREFIX_SOULBOUND, 0, NAME_PREFIX_SOULBOUND.length())) {
            return safe;
        }
        return NAME_PREFIX_SOULBOUND + safe;
    }
}