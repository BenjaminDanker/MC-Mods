package com.silver.atlantis.spawn.drop;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Efficient inventory-driven conversion of Atlantis special drops into the configured reward.
 *
 * Mirrors the Sky-Islands pattern: PlayerInventory.markDirty -> queue player UUID -> rate-limited scan on END_SERVER_TICK.
 */
public final class SpecialItemConversionManager {

    private static final String MPDS_OCEAN_DEFEATED_TAG = "mpds_ocean_defeated";

    private static final Set<UUID> PENDING_PLAYERS = new HashSet<>();
    private static final Map<UUID, Long> NEXT_ALLOWED_TICK = new HashMap<>();
    private static Item configuredSpecialDropItem;
    private static boolean configuredSpecialDropItemResolved;
    private static long TICKS;

    private static boolean initialised;

    private SpecialItemConversionManager() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        ServerTickEvents.END_SERVER_TICK.register(SpecialItemConversionManager::tick);
        AtlantisMod.LOGGER.info("Atlantis special drop conversion enabled: {} -> 1 {}",
            SpawnSpecialConfig.SPECIAL_ITEMS_PER_SEA_LANTERN,
            SpawnSpecialConfig.SPECIAL_SEA_LANTERN_DISPLAY_NAME
        );
    }

    public static void onInventoryMaybeChanged(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        PENDING_PLAYERS.add(id);
        // Delay the scan slightly; markDirty can fire repeatedly during a single action.
        NEXT_ALLOWED_TICK.putIfAbsent(id, TICKS + 2);
    }

    @SuppressWarnings("unused")
    private static void tick(MinecraftServer server) {
        TICKS++;
        if (server == null || PENDING_PLAYERS.isEmpty()) {
            return;
        }

        int threshold = Math.max(1, SpawnSpecialConfig.SPECIAL_ITEMS_PER_SEA_LANTERN);

        Iterator<UUID> it = PENDING_PLAYERS.iterator();
        while (it.hasNext()) {
            UUID id = it.next();

            long next = NEXT_ALLOWED_TICK.getOrDefault(id, 0L);
            if (TICKS < next) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                NEXT_ALLOWED_TICK.remove(id);
                continue;
            }

            tryConvert(player, threshold);

            it.remove();
            NEXT_ALLOWED_TICK.remove(id);
        }
    }

    private static void tryConvert(ServerPlayer player, int threshold) {
        int count = countSpecialDrops(player);
        int crafts = count / threshold;
        if (crafts <= 0) {
            return;
        }

        ItemStack rewardTemplate = SpecialSeaLanternItem.createOne();
        if (rewardTemplate.isEmpty()) {
            AtlantisMod.LOGGER.warn("Skipping conversion for {}: reward item could not be created", player.getScoreboardName());
            return;
        }

        tryMarkOceanDefeatedOnce(player);

        int toConsume = crafts * threshold;
        consumeSpecialDrops(player, toConsume);

        int remaining = crafts;
        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack reward = rewardTemplate.copy();
            reward.setCount(give);

            if (!player.getInventory().add(reward)) {
                if (player.level() instanceof ServerLevel world) {
                    ItemEntity drop = new ItemEntity(world, player.getX(), player.getY(), player.getZ(), reward);
                    drop.setDefaultPickUpDelay();
                    world.addFreshEntity(drop);
                }
            }

            remaining -= give;
        }
    }

    private static void tryMarkOceanDefeatedOnce(ServerPlayer player) {
        if (player == null || player.entityTags().contains(MPDS_OCEAN_DEFEATED_TAG)) {
            return;
        }

        MinecraftServer server = player.createCommandSourceStack().getServer();
        if (server == null) {
            return;
        }

        String playerName = player.getScoreboardName();
        CommandSourceStack source = server.createCommandSourceStack();

        try {
            server.getCommands().performPrefixedCommand(source, "/mpdsgrantbossreward " + playerName + " ocean 1");
            player.addTag(MPDS_OCEAN_DEFEATED_TAG);
        } catch (Exception e) {
            AtlantisMod.LOGGER.error("Failed to update MPDS ocean defeated flag for {}", playerName, e);
        }
    }

    private static int countSpecialDrops(ServerPlayer player) {
        if (player == null) {
            return 0;
        }

        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isSpecialDropStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeSpecialDrops(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (remaining <= 0) {
                break;
            }

            ItemStack stack = player.getInventory().getItem(slot);
            if (!isSpecialDropStack(stack)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    private static boolean isSpecialDropStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Item configuredItem = getConfiguredSpecialDropItem();
        if (configuredItem == null) {
            return false;
        }

        if (!stack.is(configuredItem)) {
            return false;
        }

        CompoundTag custom = readCustomData(stack);
        if (custom == null) {
            return false;
        }

        String id = custom.getString("id").orElse("");
        return "special_drop".equals(id);
    }

    private static Item getConfiguredSpecialDropItem() {
        if (configuredSpecialDropItemResolved) {
            return configuredSpecialDropItem;
        }

        configuredSpecialDropItemResolved = true;
        Identifier configuredId = Identifier.tryParse(SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
        if (configuredId == null) {
            AtlantisMod.LOGGER.warn("Invalid special drop item id in config: {}", SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
            configuredSpecialDropItem = null;
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(configuredId);
        if (item == null) {
            AtlantisMod.LOGGER.warn("Unknown special drop item in config: {}", SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
            configuredSpecialDropItem = null;
            return null;
        }

        configuredSpecialDropItem = item;
        return configuredSpecialDropItem;
    }

    private static CompoundTag readCustomData(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyTag();
    }
}
