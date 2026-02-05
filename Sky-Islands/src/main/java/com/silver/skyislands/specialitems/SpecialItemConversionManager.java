package com.silver.skyislands.specialitems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

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

    public static void onInventoryMaybeChanged(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        pendingPlayers.add(player.getUuid());
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

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
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

    private static boolean tryConvert(ServerPlayerEntity player, int feathersPerArrow) {
        int count = countSpecialFeathers(player);
        int crafts = count / feathersPerArrow;
        if (crafts <= 0) {
            return false;
        }

        int toConsume = crafts * feathersPerArrow;
        consumeSpecialFeathers(player, toConsume);

        int remaining = crafts;
        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack arrows = SpecialArrowItem.createOne();
            arrows.setCount(give);
            if (!player.getInventory().insertStack(arrows)) {
                if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world) {
                    ItemEntity drop = new ItemEntity(world, player.getX(), player.getY(), player.getZ(), arrows);
                    drop.setToDefaultPickupDelay();
                    world.spawnEntity(drop);
                }
            }
            remaining -= give;
        }

        return true;
    }

    private static int countSpecialFeathers(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (SpecialFeatherItem.isSpecialFeather(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeSpecialFeathers(ServerPlayerEntity player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = player.getInventory().getStack(slot);
            if (!SpecialFeatherItem.isSpecialFeather(stack)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }
    }
}
