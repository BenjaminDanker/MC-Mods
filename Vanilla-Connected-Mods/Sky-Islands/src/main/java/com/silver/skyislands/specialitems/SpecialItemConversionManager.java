package com.silver.skyislands.specialitems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
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

    /** Players whose reward is being granted right now, so a burst of inventory changes grants it once. */
    private static final java.util.Set<java.util.UUID> GRANTING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ExecutorService REWARD_IO = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "skyislands-boss-reward");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Grants the sky-island boss reward through MPDS's API (it raises the player's SoulboundMax once), off the server
     * thread because it talks to the database. The tag is added only after the grant succeeded, so a failure is
     * retried next time. MPDS is called by name so this mod builds without it.
     */
    private static void tryMarkSkyIslandDefeatedOnce(ServerPlayer player) {
        if (player == null || player.entityTags().contains(MPDS_SKYISLAND_DEFEATED_TAG) || !GRANTING.add(player.getUUID())) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        String name = player.getScoreboardName();
        String uuid = player.getStringUUID();
        java.util.UUID id = player.getUUID();
        REWARD_IO.execute(() -> {
            try {
                Class<?> api = Class.forName("mpds.mpds.api.BossRewards");
                java.lang.reflect.Method grant = api.getMethod("grant", String.class, String.class, String.class, int.class);
                boolean granted = (boolean) grant.invoke(null, name, uuid, "skyisland", 1);
                LOGGER.info("sky-island boss reward for {}: {}", name, granted ? "granted" : "already had it");
                server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(id);
                    if (online != null) {
                        online.addTag(MPDS_SKYISLAND_DEFEATED_TAG);
                    }
                });
            } catch (java.lang.reflect.InvocationTargetException e) {
                LOGGER.error("Failed to grant the MPDS sky-island boss reward for {}; it is retried next time", name, e.getCause());
            } catch (ReflectiveOperationException e) {
                LOGGER.error("MPDS boss-reward API not available; the sky-island reward for {} was not granted", name, e);
            } finally {
                GRANTING.remove(id);
            }
        });
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
