package com.silver.skyislands.specialitems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpecialItemConversionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpecialItemConversionManager.class);

    private static final String MPDS_SKYISLAND_DEFEATED_TAG = "mpds_skyisland_defeated";

    private static SpecialItemsConfig config;

    private static final Set<UUID> pendingPlayers = new HashSet<>();
    private static final Map<UUID, Long> nextAllowedTick = new HashMap<>();
    private static long ticks;

    private static boolean initialised;

    private SpecialItemConversionManager() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        config = SpecialItemsConfig.loadOrCreate();

        ServerTickEvents.END_SERVER_TICK.register(SpecialItemConversionManager::tick);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][specialitems] init complete feathersPerArrow={}", config == null ? -1 : config.feathersPerArrow);
        }
    }

    public static void onInventoryMaybeChanged(ServerPlayer player) {
        if (player == null) {
            return;
        }
        pendingPlayers.add(player.getUUID());
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        if (pendingPlayers.isEmpty() || server == null) {
            return;
        }

        int feathersPerArrow = config == null ? 3 : Math.max(1, config.feathersPerArrow);

        // Rate-limit checks per player to avoid doing repeated scans in the same moment
        // (markDirty can fire multiple times during a single pickup/move).
        Iterator<UUID> it = pendingPlayers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            long next = nextAllowedTick.getOrDefault(id, 0L);
            if (ticks < next) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                nextAllowedTick.remove(id);
                continue;
            }

            boolean convertedAny = tryConvert(player, feathersPerArrow);
            // After a conversion pass, we can clear pending; further conversions will be triggered by future inventory changes.
            it.remove();
            nextAllowedTick.remove(id);

            if (convertedAny) {
                // No-op; keep log quiet by default.
            }
        }
    }

    private static boolean tryConvert(ServerPlayer player, int feathersPerArrow) {
        int count = countSpecialFeathers(player);
        int crafts = count / feathersPerArrow;
        if (crafts <= 0) {
            return false;
        }

        tryMarkSkyIslandDefeatedOnce(player);

        int toConsume = crafts * feathersPerArrow;
        consumeSpecialFeathers(player, toConsume);

        int remaining = crafts;
        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack arrows = SpecialArrowItem.createOne();
            arrows.setCount(give);
            if (!player.getInventory().add(arrows)) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel world) {
                    ItemEntity drop = new ItemEntity(world, player.getX(), player.getY(), player.getZ(), arrows);
                    drop.setDefaultPickUpDelay();
                    world.addFreshEntity(drop);
                }
            }
            remaining -= give;
        }

        return true;
    }

    private static void tryMarkSkyIslandDefeatedOnce(ServerPlayer player) {
        if (player == null || player.entityTags().contains(MPDS_SKYISLAND_DEFEATED_TAG)) {
            return;
        }

        MinecraftServer server = player.createCommandSourceStack().getServer();
        if (server == null) {
            return;
        }

        String playerName = player.getScoreboardName();
        CommandSourceStack source = server.createCommandSourceStack();

        try {
            server.getCommands().performPrefixedCommand(source, "mpdsgrantbossreward " + playerName + " skyisland 1");
            player.addTag(MPDS_SKYISLAND_DEFEATED_TAG);
        } catch (Exception e) {
            LOGGER.error("Failed to update MPDS skyisland defeated flag for {}", playerName, e);
        }
    }

    private static int countSpecialFeathers(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (SpecialFeatherItem.isSpecialFeather(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeSpecialFeathers(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (!SpecialFeatherItem.isSpecialFeather(stack)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }
}
