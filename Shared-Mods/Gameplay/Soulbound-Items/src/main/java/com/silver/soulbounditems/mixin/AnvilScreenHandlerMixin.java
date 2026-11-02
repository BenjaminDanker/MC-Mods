package com.silver.soulbounditems.mixin;

import com.silver.soulbounditems.soulbound.SoulboundRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Mixin(AnvilMenu.class)
public abstract class AnvilScreenHandlerMixin {
    private static final String NAME_PREFIX_SOULBOUND = "Soulbound ";
    private static final Map<Item, Set<String>> RESERVED_SPECIAL_NAMES = Map.of(
        Items.SEA_LANTERN, Set.of("special sea lantern"),
        Items.HEART_OF_THE_SEA, Set.of("special heart of the sea"),
        Items.NETHER_STAR, Set.of("special nether star"),
        Items.FEATHER, Set.of("special feather"),
        Items.ARROW, Set.of("special arrow"),
        Items.DEAD_BUSH, Set.of("special dead bush"),
        Items.COAL_ORE, Set.of("special coal ore")
    );
    private static final Pattern SPECIAL_DRAGON_BREATH_NAME =
        Pattern.compile("special dragon breath \\(\\d+/\\d+\\)");

    @ModifyVariable(method = "setItemName", at = @At("HEAD"), argsOnly = true)
    private String soulbounditems$stripSoulboundFromRenameInput(String name) {
        String normalizedName = stripSoulboundPrefix(name);
        AnvilMenu handler = (AnvilMenu) (Object) this;
        ItemStack input = handler.getSlot(0).getItem();

        if (!isReservedSpecialName(input, normalizedName)) {
            return normalizedName;
        }

        // Keep an already legitimate mod/datapack-created name intact, but do not let an
        // anvil give an ordinary matching item the same reserved display name.
        Component existingCustomName = input.get(DataComponents.CUSTOM_NAME);
        return existingCustomName == null ? null : existingCustomName.getString();
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void soulbounditems$enforceSoulboundPrefixInAnvilOutput(CallbackInfo ci) {
        AnvilMenu handler = (AnvilMenu) (Object) this;

        Slot inputSlot = handler.getSlot(0);
        if (inputSlot == null) {
            return;
        }

        ItemStack input = inputSlot.getItem();
        if (!SoulboundRules.isSoulboundItem(input)) {
            return;
        }

        Slot outputSlot = handler.getSlot(2);
        if (outputSlot == null) {
            return;
        }

        ItemStack output = outputSlot.getItem();
        if (output == null || output.isEmpty()) {
            return;
        }

        String currentOutputName = output.getHoverName().getString();
        output.set(DataComponents.CUSTOM_NAME, Component.literal(ensureSoulboundPrefix(currentOutputName)));
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

    private static boolean isReservedSpecialName(ItemStack input, String requestedName) {
        if (input == null || input.isEmpty() || requestedName == null) {
            return false;
        }

        String normalizedRequestedName = requestedName.trim().toLowerCase(Locale.ROOT);
        Set<String> reservedNames = RESERVED_SPECIAL_NAMES.get(input.getItem());
        if (reservedNames != null && reservedNames.contains(normalizedRequestedName)) {
            return true;
        }

        return input.is(Items.DRAGON_BREATH)
            && SPECIAL_DRAGON_BREATH_NAME.matcher(normalizedRequestedName).matches();
    }
}
