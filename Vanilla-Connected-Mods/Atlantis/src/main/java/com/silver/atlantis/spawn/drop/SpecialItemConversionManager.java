package com.silver.atlantis.spawn.drop;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

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

    public static void onInventoryMaybeChanged(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUuid();
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

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
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

    private static void tryConvert(ServerPlayerEntity player, int threshold) {
        int count = countSpecialDrops(player);
        int crafts = count / threshold;
        if (crafts <= 0) {
            return;
        }

        ItemStack rewardTemplate = SpecialSeaLanternItem.createOne();
        if (rewardTemplate.isEmpty()) {
            AtlantisMod.LOGGER.warn("Skipping conversion for {}: reward item could not be created", player.getNameForScoreboard());
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

            if (!player.getInventory().insertStack(reward)) {
                if (player.getEntityWorld() instanceof ServerWorld world) {
                    ItemEntity drop = new ItemEntity(world, player.getX(), player.getY(), player.getZ(), reward);
                    drop.setToDefaultPickupDelay();
                    world.spawnEntity(drop);
                }
            }

            remaining -= give;
        }
    }

    private static void tryMarkOceanDefeatedOnce(ServerPlayerEntity player) {
        if (player == null || player.getCommandTags().contains(MPDS_OCEAN_DEFEATED_TAG)) {
            return;
        }

        MinecraftServer server = player.getCommandSource().getServer();
        if (server == null) {
            return;
        }

        String playerName = player.getNameForScoreboard();
        ServerCommandSource source = server.getCommandSource();

        try {
            server.getCommandManager().executeWithPrefix(source, "/mpdsgrantbossreward " + playerName + " ocean 1");
            player.addCommandTag(MPDS_OCEAN_DEFEATED_TAG);
        } catch (Exception e) {
            AtlantisMod.LOGGER.error("Failed to update MPDS ocean defeated flag for {}", playerName, e);
        }
    }

    private static int countSpecialDrops(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        int total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isSpecialDropStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeSpecialDrops(ServerPlayerEntity player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (remaining <= 0) {
                break;
            }

            ItemStack stack = player.getInventory().getStack(slot);
            if (!isSpecialDropStack(stack)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
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

        if (!stack.isOf(configuredItem)) {
            return false;
        }

        NbtCompound custom = readCustomData(stack);
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

        Item item = Registries.ITEM.get(configuredId);
        if (item == null) {
            AtlantisMod.LOGGER.warn("Unknown special drop item in config: {}", SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
            configuredSpecialDropItem = null;
            return null;
        }

        configuredSpecialDropItem = item;
        return configuredSpecialDropItem;
    }

    private static NbtCompound readCustomData(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyNbt();
    }
}
