package com.silver.atlantis.compass;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.cycle.CycleJsonIO;
import com.silver.atlantis.cycle.CyclePaths;
import com.silver.atlantis.cycle.CycleState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AtlantisCompassOverrideManager {
    private static final String ROOT_KEY = "atlantisCompassOverride";
    private static final String ACTIVE_KEY = "active";
    private static final String NEXT_CHARGE_TICK_KEY = "nextChargeTick";
    private static final long CENTER_REFRESH_TICKS = 20000L;
    private static final long DISCOVERY_INTERVAL_TICKS = 200L;

    private static volatile AtlantisCompassConfig config;
    private static volatile boolean initialised;
    private static long ticks;
    private static long cachedCenterTick = Long.MIN_VALUE;
    private static BlockPos cachedAtlantisCenter;
    private static final Set<UUID> trackedPlayers = new HashSet<>();
    private static final Map<UUID, Long> nextScanTickByPlayer = new HashMap<>();

    private AtlantisCompassOverrideManager() {
    }

    public static void init() {
        if (initialised) {
            return;
        }

        AtlantisCompassConfig loaded = AtlantisCompassConfig.loadOrCreateStrict();
        config = loaded;

        UseItemCallback.EVENT.register(AtlantisCompassOverrideManager::onUseItem);
        ServerTickEvents.END_SERVER_TICK.register(AtlantisCompassOverrideManager::onEndServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ticks = 0L;
            cachedCenterTick = Long.MIN_VALUE;
            cachedAtlantisCenter = null;
            trackedPlayers.clear();
            nextScanTickByPlayer.clear();
        });

        initialised = true;
        AtlantisMod.LOGGER.info("[Atlantis][compass] loaded strict config path={} crystalIntervalSeconds={}",
            AtlantisCompassConfig.configPath(),
            loaded.crystalIntervalSeconds
        );
    }

    private static InteractionResult onUseItem(net.minecraft.world.entity.player.Player player, Level world, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.COMPASS)) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        if (isActive(stack)) {
            deactivateCompass(stack);
            updatePlayerTracking(serverPlayer);
            serverPlayer.sendSystemMessage(Component.literal("Atlantis compass: OFF"), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos atlantisCenter = resolveAtlantisCenterOnUse(world.getServer());
        if (atlantisCenter == null) {
            serverPlayer.sendSystemMessage(Component.literal("Atlantis compass: no center yet"), true);
            return InteractionResult.FAIL;
        }

        if (!consumeCrystal(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.literal("Atlantis compass: need 1 prismarine crystals"), true);
            return InteractionResult.FAIL;
        }

        AtlantisCompassConfig activeConfig = getConfig();
        long intervalTicks = Math.max(1L, activeConfig.crystalIntervalTicks());
        long nowTick = world.getGameTime();
        long nextChargeTick = nowTick + intervalTicks;
        activateCompass(stack, atlantisCenter, nextChargeTick);
        trackPlayer(serverPlayer, nextChargeTick);
        serverPlayer.sendSystemMessage(Component.literal("Atlantis compass: ON (" + activeConfig.crystalIntervalSeconds + "s/crystal)"), true);
        return InteractionResult.SUCCESS;
    }

    private static void onEndServerTick(MinecraftServer server) {
        ticks++;
        AtlantisCompassConfig activeConfig = getConfig();
        long intervalTicks = Math.max(1L, activeConfig.crystalIntervalTicks());
        long nowTick = currentGameTick(server);

        if (ticks % DISCOVERY_INTERVAL_TICKS == 0L) {
            discoverTrackedPlayers(server, nowTick, intervalTicks);
        }

        Iterator<UUID> trackedIterator = trackedPlayers.iterator();
        while (trackedIterator.hasNext()) {
            UUID playerId = trackedIterator.next();
            long nextScanTick = nextScanTickByPlayer.getOrDefault(playerId, nowTick);
            if (nowTick < nextScanTick) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                trackedIterator.remove();
                nextScanTickByPlayer.remove(playerId);
                continue;
            }

            long earliestNextScan = processPlayerCompasses(player, server, intervalTicks, nowTick);
            if (earliestNextScan == Long.MAX_VALUE) {
                trackedIterator.remove();
                nextScanTickByPlayer.remove(playerId);
            } else {
                nextScanTickByPlayer.put(playerId, earliestNextScan);
            }
        }
    }

    private static long processPlayerCompasses(ServerPlayer player, MinecraftServer server, long intervalTicks, long nowTick) {
        Inventory inventory = player.getInventory();
        boolean hasActiveCompass = false;
        long earliestNextScan = Long.MAX_VALUE;
        BlockPos atlantisCenter = null;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(Items.COMPASS) || !isActive(stack)) {
                continue;
            }

            hasActiveCompass = true;
            long dueTick = normalizeDueTick(getNextChargeTick(stack), nowTick, intervalTicks);
            if (nowTick < dueTick) {
                earliestNextScan = Math.min(earliestNextScan, dueTick);
                continue;
            }

            if (atlantisCenter == null) {
                atlantisCenter = cachedAtlantisCenter;
            }
            if (atlantisCenter == null) {
                deactivateCompass(stack);
                player.sendSystemMessage(Component.literal("Atlantis compass: OFF (no center)"), true);
                continue;
            }

            if (!consumeCrystal(player)) {
                deactivateCompass(stack);
                player.sendSystemMessage(Component.literal("Atlantis compass: OFF (out of crystals)"), true);
                continue;
            }

            long nextChargeTick = nowTick + intervalTicks;
            activateCompass(stack, atlantisCenter, nextChargeTick);
            earliestNextScan = Math.min(earliestNextScan, nextChargeTick);
        }

        if (!hasActiveCompass) {
            return Long.MAX_VALUE;
        }

        if (earliestNextScan == Long.MAX_VALUE) {
            earliestNextScan = nowTick + 20L;
        }
        return earliestNextScan;
    }

    private static void discoverTrackedPlayers(MinecraftServer server, long nowTick, long intervalTicks) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (trackedPlayers.contains(player.getUUID())) {
                continue;
            }

            long earliestDue = findEarliestActiveCompassDueTick(player, nowTick, intervalTicks);
            if (earliestDue == Long.MAX_VALUE) {
                continue;
            }

            trackedPlayers.add(player.getUUID());
            nextScanTickByPlayer.put(player.getUUID(), earliestDue);
        }
    }

    private static long findEarliestActiveCompassDueTick(ServerPlayer player, long nowTick, long intervalTicks) {
        Inventory inventory = player.getInventory();
        long earliestDue = Long.MAX_VALUE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(Items.COMPASS) || !isActive(stack)) {
                continue;
            }
            long dueTick = normalizeDueTick(getNextChargeTick(stack), nowTick, intervalTicks);
            earliestDue = Math.min(earliestDue, dueTick);
        }
        return earliestDue;
    }

    private static void trackPlayer(ServerPlayer player, long nextChargeTick) {
        UUID playerId = player.getUUID();
        trackedPlayers.add(playerId);
        long existing = nextScanTickByPlayer.getOrDefault(playerId, Long.MAX_VALUE);
        nextScanTickByPlayer.put(playerId, Math.min(existing, nextChargeTick));
    }

    private static void updatePlayerTracking(ServerPlayer player) {
        UUID playerId = player.getUUID();
        AtlantisCompassConfig activeConfig = getConfig();
        long intervalTicks = Math.max(1L, activeConfig.crystalIntervalTicks());
        long nowTick = player.level().getGameTime();
        long earliestDue = findEarliestActiveCompassDueTick(player, nowTick, intervalTicks);
        if (earliestDue == Long.MAX_VALUE) {
            trackedPlayers.remove(playerId);
            nextScanTickByPlayer.remove(playerId);
            return;
        }

        trackedPlayers.add(playerId);
        nextScanTickByPlayer.put(playerId, earliestDue);
    }

    private static void activateCompass(ItemStack stack, BlockPos target, long nextChargeTick) {
        GlobalPos globalTarget = GlobalPos.of(Level.OVERWORLD, target);
        LodestoneTracker tracker = new LodestoneTracker(Optional.of(globalTarget), false);
        stack.set(DataComponents.LODESTONE_TRACKER, tracker);

        CompoundTag custom = readCustomData(stack);
        CompoundTag root = custom.getCompound(ROOT_KEY).orElse(new CompoundTag());
        root.putBoolean(ACTIVE_KEY, true);
        root.putLong(NEXT_CHARGE_TICK_KEY, nextChargeTick);
        custom.put(ROOT_KEY, root);
        writeCustomData(stack, custom);
    }

    private static void deactivateCompass(ItemStack stack) {
        stack.remove(DataComponents.LODESTONE_TRACKER);

        CompoundTag custom = readCustomData(stack);
        CompoundTag root = custom.getCompound(ROOT_KEY).orElse(null);
        if (root == null) {
            writeCustomData(stack, custom);
            return;
        }

        root.remove(ACTIVE_KEY);
        root.remove(NEXT_CHARGE_TICK_KEY);

        if (root.isEmpty()) {
            custom.remove(ROOT_KEY);
        } else {
            custom.put(ROOT_KEY, root);
        }

        writeCustomData(stack, custom);
    }

    private static boolean isActive(ItemStack stack) {
        CompoundTag custom = readCustomData(stack);
        CompoundTag root = custom.getCompound(ROOT_KEY).orElse(null);
        if (root == null) {
            return false;
        }
        return root.getBoolean(ACTIVE_KEY).orElse(false);
    }

    private static long getNextChargeTick(ItemStack stack) {
        CompoundTag custom = readCustomData(stack);
        CompoundTag root = custom.getCompound(ROOT_KEY).orElse(null);
        if (root == null) {
            return Long.MAX_VALUE;
        }
        return root.getLong(NEXT_CHARGE_TICK_KEY).orElse(Long.MAX_VALUE);
    }

    private static CompoundTag readCustomData(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? new CompoundTag() : custom.copyTag();
    }

    private static void writeCustomData(ItemStack stack, CompoundTag nbt) {
        if (nbt.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    private static BlockPos resolveAtlantisCenterOnUse(MinecraftServer server) {
        if (server == null) {
            return null;
        }

        if (cachedAtlantisCenter != null && ticks - cachedCenterTick < CENTER_REFRESH_TICKS) {
            return cachedAtlantisCenter;
        }

        CycleState state = CycleJsonIO.tryRead(CyclePaths.stateFile());
        if (state == null || state.lastCenterX() == null || state.lastCenterY() == null || state.lastCenterZ() == null) {
            cachedAtlantisCenter = null;
            cachedCenterTick = Long.MIN_VALUE;
            return null;
        }

        cachedAtlantisCenter = new BlockPos(state.lastCenterX(), state.lastCenterY(), state.lastCenterZ());
        cachedCenterTick = ticks;
        return cachedAtlantisCenter;
    }

    private static boolean consumeCrystal(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.is(Items.PRISMARINE_CRYSTALS) || stack.isEmpty()) {
                continue;
            }

            stack.shrink(1);
            inv.setChanged();
            return true;
        }
        return false;
    }

    private static AtlantisCompassConfig getConfig() {
        AtlantisCompassConfig active = config;
        if (active == null) {
            throw new IllegalStateException("AtlantisCompassConfig not initialized");
        }
        return active;
    }

    private static long currentGameTick(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return ticks;
        }
        return server.overworld().getGameTime();
    }

    private static long normalizeDueTick(long storedDueTick, long nowTick, long intervalTicks) {
        if (storedDueTick == Long.MAX_VALUE || storedDueTick <= 0L) {
            return nowTick + intervalTicks;
        }

        long maxReasonableFuture = nowTick + Math.max(intervalTicks * 4L, 200L);
        if (storedDueTick > maxReasonableFuture) {
            return nowTick + intervalTicks;
        }

        return storedDueTick;
    }
}
