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

    /** Where the blacksmith's answers go: the AI chat, or a scripted dialogue screen. */
    @FunctionalInterface
    public interface Reply {
        void say(String text);
    }

    /** Scripted dialogue: quote making the held item Soulbound (sets a pending quote on the session when possible). */
    public static void quote(ServerPlayer player, ConversationSession session, Reply reply) {
        // The dialogue screen offers its own confirm button, so the chat instruction is left out.
        handleModify(player, session, "!modify Soulbound", text -> reply.say(text.replace("\n" + CONFIRM_PROMPT, "")));
    }

    /** Scripted dialogue: carry out the pending quote. */
    public static void confirm(ServerPlayer player, ConversationSession session, Reply reply) {
        handleConfirm(player, session, reply);
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
                + "The price of a Soulbound modification is one more of the same item, or a spare fragment from the four worlds: "
                + "a second Special Arrow, Special Sea Lantern, Special Dead Bush or Special Coal Ore (the player always keeps one). "
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
        Reply reply = text -> manager.sendDeterministicReply(player, session, text);
        if (lower.startsWith("!modify")) {
            return handleModify(player, session, trimmed, reply);
        }

        if (lower.equals("!confirm")) {
            return handleConfirm(player, session, reply);
        }

        return false;
    }

    private static boolean handleModify(ServerPlayer player, ConversationSession session, String message, Reply reply) {
        // Any new modify attempt invalidates any previously-quoted modification.
        session.setPendingModification(null);

        String[] parts = message.split("\\s+", 3);
        if (parts.length < 2 || !MOD_SOULBOUND.equalsIgnoreCase(parts[1])) {
            reply.say("Only !modify Soulbound is supported.");
            return true;
        }

        ItemStack main = player.getInventory().getSelectedItem();
        if (main == null || main.isEmpty()) {
            reply.say("Hold exactly one item in your main hand, then ask again.");
            return true;
        }

        if (main.getCount() != 1) {
            reply.say("Hold exactly one item, not a stack, then ask again.");
            return true;
        }

        if (isSoulboundTagged(main) || fragmentId(main) != null) {
            reply.say("That is already Soulbound.");
            return true;
        }

        Payment quoted = findPayment(player, main.getItem());
        if (quoted == null) {
            String itemName = safeItemName(main);
            reply.say("Bring one additional " + itemName + " as payment, or a spare fragment from one of the four worlds, then try again.");
            return true;
        }

        withSoulboundMax(player, soulboundMax -> {
            int soulboundCount = countSoulboundItems(player);
            if (soulboundCount >= soulboundMax) {
                reply.say("Your Soul isn't large enough or you have too many Soulbound items.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.");
                return;
            }

            PendingModification pending = new PendingModification(main.getItem(), MOD_SOULBOUND);
            session.setPendingModification(pending);

            reply.say("The cost is " + quoted.label() + ". Soulbound items travel across dimensions and are not dropped on death, but can still be manually dropped and used by others. This cannot be undone by normal means.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.\n" + CONFIRM_PROMPT);
        }, () -> reply.say("The Soulbound capacity system is unavailable. Try again later."));
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

    private static boolean handleConfirm(ServerPlayer player, ConversationSession session, Reply reply) {
        if (session.getPendingModification() == null) {
            // Ignore stray confirmations unless a prior !modify created a pending quote.
            return true;
        }
        // Everything is checked again after the cap arrives: the player may have changed hands in the meantime.
        withSoulboundMax(player, soulboundMax -> confirmWithCap(player, session, soulboundMax, reply), () -> {
            session.setPendingModification(null);
            reply.say("The Soulbound capacity system is unavailable. Try again later.");
        });
        return true;
    }

    private static void confirmWithCap(ServerPlayer player, ConversationSession session, int soulboundMax, Reply reply) {
        PendingModification pending = session.getPendingModification();
        if (pending == null) {
            return;
        }

        ItemStack main = player.getInventory().getSelectedItem();
        if (main == null || main.isEmpty() || main.getItem() != pending.item()) {
            session.setPendingModification(null);
            reply.say("The pending quote was cancelled because you are not holding the expected item. Ask again when you have it.");
            return;
        }

        if (main.getCount() != 1) {
            session.setPendingModification(null);
            reply.say("Hold exactly one item, not a stack, then retry with !modify Soulbound.");
            return;
        }

        Payment payment = findPayment(player, pending.item());
        if (payment == null) {
            session.setPendingModification(null);
            String itemName = safeItemName(main);
            reply.say("You no longer have the payment (an additional " + itemName + " or a spare fragment). Ask again when you have it.");
            return;
        }

        int soulboundCount = countSoulboundItems(player);
        if (soulboundCount >= soulboundMax) {
            session.setPendingModification(null);
            reply.say("Your Soul isn't large enough or you have too many Soulbound items.\nYour Soulbound capacity is " + soulboundCount + " out of a maximum of " + soulboundMax + " items.");
            return;
        }

        // Consume payment item.
        ItemStack paid = player.getInventory().getItem(payment.slot());
        paid.shrink(1);
        if (paid.isEmpty()) {
            player.getInventory().setItem(payment.slot(), ItemStack.EMPTY);
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
        reply.say(itemName + " is now Soulbound. It travels across dimensions and is not dropped on death, but can still be manually dropped and used by others. This cannot be undone by normal means.");
        return;
    }

    /** The four worlds' keys; a player carrying two of one may pay with the spare and keep the other. */
    private static final java.util.Set<String> FRAGMENTS = java.util.Set.of("special_arrow", "special_sea_lantern", "special_dead_bush", "special_coal_ore");

    private record Payment(int slot, String label) {
    }

    /** One more of the held item, or else a spare fragment (never a player's only one of it); null when neither is carried. */
    private static Payment findPayment(ServerPlayer player, Item item) {
        int copy = findPaymentSlot(player, item);
        if (copy != -1) {
            return new Payment(copy, "one additional " + safeItemName(player.getInventory().getItem(copy)));
        }
        int selected = player.getInventory().getSelectedSlot();
        int limit = Math.min(36, player.getInventory().getContainerSize());
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, Integer> slots = new java.util.HashMap<>();
        for (int i = 0; i < limit; i++) {
            String fragment = fragmentId(player.getInventory().getItem(i));
            if (fragment != null) {
                counts.merge(fragment, player.getInventory().getItem(i).getCount(), Integer::sum);
                if (i != selected) {
                    slots.putIfAbsent(fragment, i);
                }
            }
        }
        for (var entry : slots.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) >= 2) {
                return new Payment(entry.getValue(), "one spare " + safeItemName(player.getInventory().getItem(entry.getValue())) + " (you keep the other)");
            }
        }
        return null;
    }

    private static String fragmentId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String id = data.copyTag().getString("id").orElse("");
        return FRAGMENTS.contains(id) ? id : null;
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
