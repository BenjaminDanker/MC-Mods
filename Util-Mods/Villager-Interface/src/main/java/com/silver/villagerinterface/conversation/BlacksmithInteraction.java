package com.silver.villagerinterface.conversation;

import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.soulbound.MpdsSoulboundApi;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

public final class BlacksmithInteraction {
    private static final String TYPE_BLACKSMITH = "blacksmith";
    private static final String MOD_SOULBOUND = "Soulbound";
    private static final String MOD_SOULBOUND_CRAFTED = "CraftedSoulbound";
    private static final String NAME_PREFIX_SOULBOUND = "Soulbound ";
    private static final String CONFIRM_PROMPT = "Please type !confirm to modify this item";
    private static final String KEY_ID_TYPE = "idType";

    private BlacksmithInteraction() {
    }

    public static boolean isBlacksmith(VillagerConfigEntry entry) {
        if (entry == null || entry.villagerType() == null) {
            return false;
        }
        return TYPE_BLACKSMITH.equals(entry.villagerType().trim().toLowerCase(Locale.ROOT));
    }

    public static void addBlacksmithSystemRules(ConversationSession session) {
        session.addSystemMessage(
            "You are a blacksmith villager. You can discuss modifications and how to use commands, but you must never invent prices, coins, or currencies. "
                + "Soulbound in this server is NOT an ownership lock: do not say it prevents other players from using the item. "
                + "Soulbound also does NOT stop the player from manually dropping the item; do not claim it prevents accidental dropping/removal. "
                + "Its purpose is to keep the item with the player across dimension travel and to not be dropped on death. "
                + "Do NOT mention internal implementation details like NBT, custom data, tags, or keys (for example: idType=...). "
                + "The only way to start a Soulbound modification is the player typing exactly: !modify Soulbound. "
                + "Do NOT tell the player to type !confirm unless the server explicitly tells you that a modification quote is pending confirmation."
        );
    }

    public static boolean handleCommand(ConversationManager manager, ServerPlayerEntity player, ConversationSession session, String rawMessage) {
        String trimmed = rawMessage != null ? rawMessage.trim() : "";
        if (trimmed.isEmpty() || !trimmed.startsWith("!")) {
            return false;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("!modify")) {
            return handleModify(manager, player, session, trimmed);
        }

        if (lower.equals("!confirm")) {
            return handleConfirm(manager, player, session);
        }

        return false;
    }

    private static boolean handleModify(ConversationManager manager, ServerPlayerEntity player, ConversationSession session, String message) {
        // Any new modify attempt invalidates any previously-quoted modification.
        session.setPendingModification(null);

        String[] parts = message.split("\\s+", 3);
        if (parts.length < 2 || !MOD_SOULBOUND.equalsIgnoreCase(parts[1])) {
            String strict = "Strict rules: Only command supported is !modify Soulbound. Do not mention !confirm. Do not mention coins. Reply briefly with correct usage.";
            manager.requestTransientReply(player, session, "The player attempted an invalid blacksmith modification command.", strict);
            return true;
        }

        ItemStack main = player.getMainHandStack();
        if (main == null || main.isEmpty()) {
            String strict = "Strict rules: The player must hold exactly one item in their main hand to use !modify Soulbound. Do not mention !confirm. Do not mention coins.";
            manager.requestTransientReply(player, session, "The player wants a Soulbound modification.", strict);
            return true;
        }

        if (main.getCount() != 1) {
            String strict = "Strict rules: The player must hold exactly one item (not a stack) in their main hand. Do not mention !confirm. Do not mention coins.";
            manager.requestTransientReply(player, session, "The player wants a Soulbound modification.", strict);
            return true;
        }

        int paymentSlot = findPaymentSlot(player, main.getItem());
        if (paymentSlot == -1) {
            String itemName = safeItemName(main);
            String strict = "Strict rules: Cost is 1x additional item of the same type from inventory. No coins. The player lacks the required payment item. Tell them to bring another " + itemName + ". Do not mention !confirm.";
            manager.requestTransientReply(player, session, "The player wants a Soulbound modification.", strict);
            return true;
        }

        int soulboundMax;
        try {
            soulboundMax = MpdsSoulboundApi.getSoulboundMax(player.getName().getString(), player.getUuidAsString());
        } catch (Exception e) {
            String strict = "Strict rules: The Soulbound capacity system is unavailable right now (DB error). Tell the player to try again later. Do not mention coins. Do not mention !confirm.";
            manager.requestTransientReply(player, session, "The player wants a Soulbound modification but DB lookup failed.", strict);
            return true;
        }

        int soulboundCount = countSoulboundItems(player);
        if (soulboundCount >= soulboundMax) {
            String strict = "Strict rules: The player has no remaining Soulbound crafting capacity. Use this phrasing: 'Your Soul isn't large enough or you have too many Soulbound items.' "
                + "If you mention numbers, include this exact sentence on its own line: 'Your Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.' "
                + "Do not mention coins. Do not mention !confirm.";
            manager.requestTransientReply(player, session, "The player wants a Soulbound modification but has no capacity.", strict);
            return true;
        }

        PendingModification pending = new PendingModification(main.getItem(), MOD_SOULBOUND);
        session.setPendingModification(pending);

        String itemName = safeItemName(main);
        String strict = "A Soulbound modification quote is pending confirmation. Exact cost: consume 1x additional " + itemName + " (same item type) from the player's inventory. "
            + "Do not mention coins. Explain the modification as: the item travels with the player across dimensions and is not dropped on death. "
            + "Important: it CAN still be manually dropped by the player; do NOT claim it prevents accidental dropping/removal. "
            + "Do NOT claim it prevents other players from using the item. "
            + "Add a warning: Soulbound cannot be undone by normal means. "
                + "Include this exact sentence on its own line: 'Your Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.' "
            + "Then include this exact sentence on its own line: \"" + CONFIRM_PROMPT + "\"";
        manager.requestTransientReply(player, session, "Quote and explain the Soulbound modification for the player's selected item.", strict);
        return true;
    }

    private static boolean handleConfirm(ConversationManager manager, ServerPlayerEntity player, ConversationSession session) {
        PendingModification pending = session.getPendingModification();
        if (pending == null) {
            // Ignore stray confirmations unless a prior !modify created a pending quote.
            return true;
        }

        ItemStack main = player.getMainHandStack();
        if (main == null || main.isEmpty() || main.getItem() != pending.item()) {
            session.setPendingModification(null);
            String strict = "Strict rules: The player is not holding the expected item anymore. The pending quote was cancelled. Tell them to retry with !modify Soulbound. Do not mention coins.";
            manager.requestTransientReply(player, session, "The player tried to confirm a blacksmith modification.", strict);
            return true;
        }

        if (main.getCount() != 1) {
            session.setPendingModification(null);
            String strict = "Strict rules: The player must hold exactly one item (not a stack). Tell them to hold a single item then retry !modify Soulbound. Do not mention coins.";
            manager.requestTransientReply(player, session, "The player tried to confirm a blacksmith modification.", strict);
            return true;
        }

        int paymentSlot = findPaymentSlot(player, pending.item());
        if (paymentSlot == -1) {
            session.setPendingModification(null);
            String itemName = safeItemName(main);
            String strict = "Strict rules: The player no longer has the required cost item. Cost is 1x additional " + itemName + " (same item type). No coins. Tell them to bring another and retry !modify Soulbound.";
            manager.requestTransientReply(player, session, "The player tried to confirm a blacksmith modification.", strict);
            return true;
        }

        int soulboundMax;
        try {
            soulboundMax = MpdsSoulboundApi.getSoulboundMax(player.getName().getString(), player.getUuidAsString());
        } catch (Exception e) {
            session.setPendingModification(null);
            String strict = "Strict rules: The Soulbound capacity system is unavailable right now (DB error). Tell the player to try again later and retry !modify Soulbound. Do not mention coins. Do not mention !confirm.";
            manager.requestTransientReply(player, session, "The player tried to confirm a Soulbound modification but DB lookup failed.", strict);
            return true;
        }

        int soulboundCount = countSoulboundItems(player);
        if (soulboundCount >= soulboundMax) {
            session.setPendingModification(null);
            String strict = "Strict rules: The player has no remaining Soulbound crafting capacity. Use this phrasing: 'Your Soul isn't large enough or you have too many Soulbound items.' "
                + "If you mention numbers, include this exact sentence on its own line: 'Your Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.' "
                + "Do not mention coins. Do not mention !confirm.";
            manager.requestTransientReply(player, session, "The player tried to confirm a Soulbound modification but has no capacity.", strict);
            return true;
        }

        // Consume payment item.
        ItemStack payment = player.getInventory().getStack(paymentSlot);
        payment.decrement(1);
        if (payment.isEmpty()) {
            player.getInventory().setStack(paymentSlot, ItemStack.EMPTY);
        }

        // Replace main-hand item with a Soulbound-tagged copy.
        ItemStack modified = main.copy();
        modified.setCount(1);

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, modified, nbt -> {
            nbt.putString(KEY_ID_TYPE, MOD_SOULBOUND_CRAFTED);
        });

        modified.set(DataComponentTypes.CUSTOM_NAME, Text.literal(ensureSoulboundPrefix(modified.getName().getString())));

        player.getInventory().setSelectedStack(modified);
        player.getInventory().markDirty();

        session.setPendingModification(null);
        String itemName = safeItemName(modified);
        String strict = "The deal is complete. Cost consumed: 1x additional " + itemName + " (same item type). The selected item is now Soulbound. "
            + "Soulbound means the item travels with the player across dimensions and is not dropped on death; it CAN still be manually dropped; it does NOT prevent other players from using it. "
            + "Remind them it cannot be undone by normal means. "
            + "Do not mention coins. Provide a short confirmation to the player.";
        manager.requestTransientReply(player, session, "Confirm the Soulbound modification is complete.", strict);
        return true;
    }

    private static int findPaymentSlot(ServerPlayerEntity player, Item item) {
        int selected = player.getInventory().getSelectedSlot();
        int limit = Math.min(36, player.getInventory().size());
        for (int i = 0; i < limit; i++) {
            if (i == selected) {
                continue;
            }
            ItemStack stack = player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private static int countSoulboundItems(ServerPlayerEntity player) {
        int count = 0;
        int size = player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isSoulboundTagged(stack)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isSoulboundTagged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        String idType = customData.copyNbt().getString(KEY_ID_TYPE).orElse("");
        return MOD_SOULBOUND_CRAFTED.equals(idType);
    }

    private static String safeItemName(ItemStack stack) {
        try {
            return stack.getName().getString();
        } catch (Exception ignored) {
            return "item";
        }
    }

    private static String ensureSoulboundPrefix(String name) {
        String safe = name == null ? "" : name.trim();
        if (safe.isEmpty()) {
            return NAME_PREFIX_SOULBOUND.trim();
        }
        if (safe.regionMatches(true, 0, NAME_PREFIX_SOULBOUND, 0, NAME_PREFIX_SOULBOUND.length())) {
            return safe;
        }
        return NAME_PREFIX_SOULBOUND + safe;
    }

    public record PendingModification(Item item, String modificationType) {
    }
}
