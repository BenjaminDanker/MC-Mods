package com.silver.villagerinterface.conversation;

import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.soulbounditems.mpds.MpdsSoulboundApi;
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

        withSoulboundMax(player, soulboundMax -> {
            int soulboundCount = countSoulboundItems(player);
            if (soulboundCount >= soulboundMax) {
                manager.sendDeterministicReply(player, session, "Your Soul isn't large enough or you have too many Soulbound items.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.");
                return;
            }

            PendingModification pending = new PendingModification(main.getItem(), MOD_SOULBOUND);
            session.setPendingModification(pending);

            String itemName = safeItemName(main);
            manager.sendDeterministicReply(player, session, "The cost is one additional " + itemName + ". Soulbound items travel across dimensions and are not dropped on death, but can still be manually dropped and used by others. This cannot be undone by normal means.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.\n" + CONFIRM_PROMPT);
        }, () -> manager.sendDeterministicReply(player, session, "The Soulbound capacity system is unavailable. Try again later."));
        return true;
    }

    private static final java.util.concurrent.ExecutorService CAP_LOOKUP = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "blacksmith-soulbound-cap");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Reads the player's Soulbound cap off the server thread (it can query the database) and continues on the server
     * thread, only while the same player is still online. {@code onUnavailable} runs when the cap can't be read.
     */
    private static void withSoulboundMax(ServerPlayer player, java.util.function.IntConsumer onValue, Runnable onUnavailable) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        String name = player.getName().getString();
        String uuid = player.getUUID().toString();
        java.util.UUID id = player.getUUID();
        CAP_LOOKUP.execute(() -> {
            Integer max;
            try {
                max = MpdsSoulboundApi.getSoulboundMax(name, uuid);
            } catch (Exception e) {
                max = null;
            }
            Integer result = max;
            server.execute(() -> {
                if (server.getPlayerList().getPlayer(id) != player) {
                    return;
                }
                if (result == null) {
                    onUnavailable.run();
                } else {
                    onValue.accept(result);
                }
            });
        });
    }

    private static boolean handleConfirm(ConversationManager manager, ServerPlayer player, ConversationSession session) {
        if (session.getPendingModification() == null) {
            // Ignore stray confirmations unless a prior !modify created a pending quote.
            return true;
        }
        // Everything is checked again after the cap arrives: the player may have changed hands in the meantime.
        withSoulboundMax(player, soulboundMax -> confirmWithCap(manager, player, session, soulboundMax), () -> {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "The Soulbound capacity system is unavailable. Try !modify Soulbound again later.");
        });
        return true;
    }

    private static void confirmWithCap(ConversationManager manager, ServerPlayer player, ConversationSession session, int soulboundMax) {
        PendingModification pending = session.getPendingModification();
        if (pending == null) {
            return;
        }

        ItemStack main = player.getInventory().getSelectedItem();
        if (main == null || main.isEmpty() || main.getItem() != pending.item()) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "The pending quote was cancelled because you are not holding the expected item. Retry with !modify Soulbound.");
            return;
        }

        if (main.getCount() != 1) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "Hold exactly one item, not a stack, then retry with !modify Soulbound.");
            return;
        }

        int paymentSlot = findPaymentSlot(player, pending.item());
        if (paymentSlot == -1) {
            session.setPendingModification(null);
            String itemName = safeItemName(main);
            manager.sendDeterministicReply(player, session, "You no longer have the additional " + itemName + " required as payment. Retry with !modify Soulbound.");
            return;
        }

        int soulboundCount = countSoulboundItems(player);
        if (soulboundCount >= soulboundMax) {
            session.setPendingModification(null);
            manager.sendDeterministicReply(player, session, "Your Soul isn't large enough or you have too many Soulbound items.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.");
            return;
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
        return;
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
