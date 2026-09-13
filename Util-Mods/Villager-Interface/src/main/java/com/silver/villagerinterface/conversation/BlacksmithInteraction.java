package com.silver.villagerinterface.conversation;

import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.soulbound.MpdsSoulboundApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

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

    public static boolean handleCommand(ConversationManager manager, ServerPlayer player, ConversationSession session, String rawMessage) {
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

    private static boolean handleModify(ConversationManager manager, ServerPlayer player, ConversationSession session, String message) {
        // Any new modify attempt invalidates any previously-quoted modification.
        session.setPendingModification(null);

        String[] parts = message.split("\\s+", 3);
        if (parts.length < 2 || !MOD_SOULBOUND.equalsIgnoreCase(parts[1])) {
            manager.sendDeterministicReply(player, session, "Only !modify Soulbound is supported.");
            return true;
        }

        ItemStack main = player.getInventory().getSelectedItem();
        if (main == null || main.isEmpty()) {
            manager.sendDeterministicReply(player, session, "Hold exactly one item in your main hand, then use !modify Soulbound.");
            return true;
        }

        if (main.getCount() != 1) {
            manager.sendDeterministicReply(player, session, "Hold exactly one item, not a stack, then use !modify Soulbound.");
            return true;
        }

        int paymentSlot = findPaymentSlot(player, main.getItem());
        if (paymentSlot == -1) {
            String itemName = safeItemName(main);
            manager.sendDeterministicReply(player, session, "Bring one additional " + itemName + " as payment, then try again.");
            return true;
        }

        int soulboundMax;
        try {
            soulboundMax = MpdsSoulboundApi.getSoulboundMax(player.getName().getString(), player.getUUID().toString());
        } catch (Exception e) {
            manager.sendDeterministicReply(player, session, "The Soulbound capacity system is unavailable. Try again later.");
            return true;
        }

        int soulboundCount = countSoulboundItems(player);
        if (soulboundCount >= soulboundMax) {
            manager.sendDeterministicReply(player, session, "Your Soul isn't large enough or you have too many Soulbound items.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.");
            return true;
        }

        PendingModification pending = new PendingModification(main.getItem(), MOD_SOULBOUND);
        session.setPendingModification(pending);

        String itemName = safeItemName(main);
        manager.sendDeterministicReply(player, session, "The cost is one additional " + itemName + ". Soulbound items travel across dimensions and are not dropped on death, but can still be manually dropped and used by others. This cannot be undone by normal means.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.\n" + CONFIRM_PROMPT);
        return true;
    }

    private static boolean handleConfirm(ConversationManager manager, ServerPlayer player, ConversationSession session) {
        PendingModification pending = session.getPendingModification();
        if (pending == null) {
            // Ignore stray confirmations unless a prior !modify created a pending quote.
            return true;
        }

        ItemStack main = player.getInventory().getSelectedItem();
        if (main == null || main.isEmpty() || main.getItem() != pending.item()) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "The pending quote was cancelled because you are not holding the expected item. Retry with !modify Soulbound.");
            return true;
        }

        if (main.getCount() != 1) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "Hold exactly one item, not a stack, then retry with !modify Soulbound.");
            return true;
        }

        int paymentSlot = findPaymentSlot(player, pending.item());
        if (paymentSlot == -1) {
            session.setPendingModification(null);
            String itemName = safeItemName(main);
            manager.sendDeterministicReply(player, session, "You no longer have the additional " + itemName + " required as payment. Retry with !modify Soulbound.");
            return true;
        }

        int soulboundMax;
        try {
            soulboundMax = MpdsSoulboundApi.getSoulboundMax(player.getName().getString(), player.getUUID().toString());
        } catch (Exception e) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "The Soulbound capacity system is unavailable. Try !modify Soulbound again later.");
            return true;
        }

        int soulboundCount = countSoulboundItems(player);
        if (soulboundCount >= soulboundMax) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "Your Soul isn't large enough or you have too many Soulbound items.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.");
            return true;
        }

        // Consume payment item.
        ItemStack payment = player.getInventory().getItem(paymentSlot);
        payment.shrink(1);
        if (payment.isEmpty()) {
            player.getInventory().setItem(paymentSlot, ItemStack.EMPTY);
        }

        // Replace main-hand item with a Soulbound-tagged copy.
        ItemStack modified = main.copy();
        modified.setCount(1);

        CustomData.update(DataComponents.CUSTOM_DATA, modified, nbt -> {
            nbt.putString(KEY_ID_TYPE, MOD_SOULBOUND_CRAFTED);
        });

        modified.set(DataComponents.CUSTOM_NAME, Component.literal(ensureSoulboundPrefix(modified.getHoverName().getString())));

        player.getInventory().setSelectedItem(modified);
        player.getInventory().setChanged();

        session.setPendingModification(null);
        String itemName = safeItemName(modified);
        manager.sendDeterministicReply(player, session, itemName + " is now Soulbound. It travels across dimensions and is not dropped on death, but can still be manually dropped and used by others. This cannot be undone by normal means.");
        return true;
    }

    private static int findPaymentSlot(ServerPlayer player, Item item) {
        int selected = player.getInventory().getSelectedSlot();
        int limit = Math.min(36, player.getInventory().getContainerSize());
        for (int i = 0; i < limit; i++) {
            if (i == selected) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private static int countSoulboundItems(ServerPlayer player) {
        int count = 0;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isSoulboundTagged(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean isSoulboundTagged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        String idType = customData.copyTag().getString(KEY_ID_TYPE).orElse("");
        return MOD_SOULBOUND_CRAFTED.equals(idType);
    }

    private static String safeItemName(ItemStack stack) {
        try {
            return stack.getHoverName().getString();
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
