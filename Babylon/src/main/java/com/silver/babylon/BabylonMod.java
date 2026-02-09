package com.silver.babylon;

import com.silver.babylon.config.BabylonConfig;
import com.silver.babylon.config.BabylonConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BabylonMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("babylon");

    private static final String TAG_STAGE1_PROCESSING = "babylon_stage1_processing";
    private static final String TAG_STAGE1_DONE = "babylon_stage1_done";

    private static final String KEY_ID = "id";

    private static final String ID_OCEAN = "special_sea_lantern";
    private static final String ID_SKY = "special_arrow";
    private static final String ID_DESERT = "special_dead_bush";
    private static final String ID_CAVE = "special_coal_ore";

    private static final long ELIGIBILITY_CHECK_EVERY_TICKS = 10L;

    private static final Map<UUID, ZoneState> zone = new ConcurrentHashMap<>();
    private static final Map<UUID, Stage1State> stage1 = new ConcurrentHashMap<>();

    private BabylonConfig config;

    @Override
    public void onInitialize() {
        this.config = new BabylonConfigManager().loadOrCreate();

        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        LOGGER.info(
            "Babylon loaded region=({}, {}, {})..({}, {}, {}) entryDelaySeconds={} particleSeconds={} teleportCommand='{}'",
            config.region.minX, config.region.minY, config.region.minZ,
            config.region.maxX, config.region.maxY, config.region.maxZ,
            config.entryDelaySeconds,
            config.particleSeconds,
            config.teleportCommand
        );
    }

    private void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null) {
                continue;
            }

            if (!(player.getEntityWorld() instanceof ServerWorld world)) {
                continue;
            }

            BlockPos pos = player.getBlockPos();
            boolean inRegion = config.region.contains(pos.getX(), pos.getY(), pos.getZ());

            if (!inRegion) {
                ZoneState previous = zone.remove(player.getUuid());
                if (previous != null) {
                    LOGGER.info("{} left region at {}", player.getNameForScoreboard(), pos);
                }

                if (player.getCommandTags().contains(TAG_STAGE1_PROCESSING)) {
                    handleLeftRegionWhileProcessing(server, world, player);
                } else {
                    stage1.remove(player.getUuid());
                }
                continue;
            }

            if (!zone.containsKey(player.getUuid())) {
                LOGGER.info("{} entered region at {} (entryDelaySeconds={})", player.getNameForScoreboard(), pos, config.entryDelaySeconds);
            }

            if (player.getCommandTags().contains(TAG_STAGE1_PROCESSING)) {
                Stage1State state = stage1.get(player.getUuid());
                if (state == null) {
                    cancelProcessing(player);
                    zone.put(player.getUuid(), new ZoneState(world.getTime(), false, 0));
                    continue;
                }

                runParticles(world, player);
                if (world.getTime() >= state.teleportAtTick) {
                    finishAndTeleport(server, player);
                }
                continue;
            }

            ZoneState zoneState = zone.computeIfAbsent(player.getUuid(),
                u -> new ZoneState(world.getTime(), false, 0));

            long nowTick = world.getTime();
            long entryDelayTicks = Math.max(0, config.entryDelaySeconds) * 20L;
            if (nowTick - zoneState.enteredAtTick < entryDelayTicks) {
                continue;
            }

            if (zoneState.lastEligibilityCheckTick != 0
                && nowTick - zoneState.lastEligibilityCheckTick < ELIGIBILITY_CHECK_EVERY_TICKS) {
                continue;
            }
            zoneState = new ZoneState(zoneState.enteredAtTick, zoneState.notWorthyShown, nowTick);
            zone.put(player.getUuid(), zoneState);

            if (hasAllFour(player)) {
                LOGGER.info("{} is worthy; starting processing", player.getNameForScoreboard());
                startProcessing(world, player);
            } else if (!zoneState.notWorthyShown) {
                LOGGER.info("{} is not worthy; sending message", player.getNameForScoreboard());
                player.sendMessage(Text.literal(config.notWorthyMessage).formatted(Formatting.RED), true);
                zone.put(player.getUuid(), new ZoneState(zoneState.enteredAtTick, true, zoneState.lastEligibilityCheckTick));
            }
        }
    }

    private void startProcessing(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }

        ConsumedItems consumed = consumeFour(player);
        if (consumed == null) {
            return;
        }

        player.addCommandTag(TAG_STAGE1_PROCESSING);
        long nowTick = world.getTime();
        long delayTicks = Math.max(1, config.particleSeconds) * 20L;
        stage1.put(player.getUuid(), new Stage1State(nowTick + delayTicks, consumed));
        LOGGER.info("{} processing started; teleport in {}s", player.getNameForScoreboard(), Math.max(1, config.particleSeconds));
        runParticles(world, player);
    }

    private ConsumedItems consumeFour(ServerPlayerEntity player) {
        ItemStack ocean = removeOneByCustomId(player, ID_OCEAN);
        ItemStack sky = removeOneByCustomId(player, ID_SKY);
        ItemStack desert = removeOneByCustomId(player, ID_DESERT);
        ItemStack cave = removeOneByCustomId(player, ID_CAVE);

        if (ocean.isEmpty() || sky.isEmpty() || desert.isEmpty() || cave.isEmpty()) {
            // Shouldn't happen (we only start after hasAllFour), but be safe and restore.
            if (!ocean.isEmpty()) {
                tryRestore(player, ocean);
            }
            if (!sky.isEmpty()) {
                tryRestore(player, sky);
            }
            if (!desert.isEmpty()) {
                tryRestore(player, desert);
            }
            if (!cave.isEmpty()) {
                tryRestore(player, cave);
            }
            return null;
        }

        return new ConsumedItems(ocean, sky, desert, cave);
    }

    private ItemStack removeOneByCustomId(ServerPlayerEntity player, String requiredId) {
        if (player == null || requiredId == null) {
            return ItemStack.EMPTY;
        }

        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack st = player.getInventory().getStack(slot);
            if (matchesId(st, requiredId)) {
                ItemStack taken = st.copy();
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                return taken;
            }
        }

        ItemStack off = player.getOffHandStack();
        if (matchesId(off, requiredId)) {
            ItemStack taken = off.copy();
            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, ItemStack.EMPTY);
            return taken;
        }

        return ItemStack.EMPTY;
    }

    private boolean tryRestore(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return true;
        }

        // Only restore into inventory; if full, treat as failure so we can still teleport.
        return player.getInventory().insertStack(stack);
    }

    private void cancelProcessing(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        player.removeCommandTag(TAG_STAGE1_PROCESSING);
        stage1.remove(player.getUuid());
    }

    private void handleLeftRegionWhileProcessing(MinecraftServer server, ServerWorld world, ServerPlayerEntity player) {
        if (server == null || world == null || player == null) {
            return;
        }

        Stage1State state = stage1.get(player.getUuid());
        if (state == null) {
            cancelProcessing(player);
            return;
        }

        boolean restoredAll = true;
        restoredAll &= tryRestore(player, state.consumed.ocean);
        restoredAll &= tryRestore(player, state.consumed.sky);
        restoredAll &= tryRestore(player, state.consumed.desert);
        restoredAll &= tryRestore(player, state.consumed.cave);

        if (restoredAll) {
            cancelProcessing(player);
            return;
        }

        // If we couldn't restore (inventory full etc.), keep going and still teleport at expiry.
        if (world.getTime() >= state.teleportAtTick) {
            finishAndTeleport(server, player);
        }
    }

    private void runParticles(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return;
        }

        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        world.spawnParticles(player, ParticleTypes.PORTAL, true, false, x, y, z, 30, 0.6, 0.8, 0.6, 0.02);
    }

    private void finishAndTeleport(MinecraftServer server, ServerPlayerEntity player) {
        if (server == null || player == null) {
            return;
        }

        player.removeCommandTag(TAG_STAGE1_PROCESSING);
        stage1.remove(player.getUuid());
        zone.remove(player.getUuid());

        String playerName = player.getNameForScoreboard();
        ServerCommandSource source = server.getCommandSource();

        String cmd = normalizeCommand(config.teleportCommand);

        server.execute(() -> {
            try {
                String c1 = "mpdsstage1 " + playerName + " true";
                String c2 = "mpdsremovecustomid " + playerName + " id " + ID_OCEAN;
                String c3 = "mpdsremovecustomid " + playerName + " id " + ID_SKY;
                String c4 = "mpdsremovecustomid " + playerName + " id " + ID_DESERT;
                String c5 = "mpdsremovecustomid " + playerName + " id " + ID_CAVE;

                LOGGER.info("Running stage1 commands for {}: '{}', '{}', '{}', '{}', '{}'", playerName, c1, c2, c3, c4, c5);
                server.getCommandManager().executeWithPrefix(source, c1);
                server.getCommandManager().executeWithPrefix(source, c2);
                server.getCommandManager().executeWithPrefix(source, c3);
                server.getCommandManager().executeWithPrefix(source, c4);
                server.getCommandManager().executeWithPrefix(source, c5);

                if (cmd != null && !cmd.isBlank()) {
                    LOGGER.info("Running teleport command for {} as player: '{}'", playerName, cmd);
                    server.getCommandManager().executeWithPrefix(player.getCommandSource(), cmd);
                } else {
                    LOGGER.warn("Teleport command is blank; nothing to run for {}", playerName);
                }

                LOGGER.info("{} stage1 done", playerName);
            } catch (Exception e) {
                LOGGER.error("Stage1 teleport failed for {}", playerName, e);
            }
        });
    }

    private boolean hasAllFour(ServerPlayerEntity player) {
        return hasCustomId(player, ID_OCEAN)
            && hasCustomId(player, ID_SKY)
            && hasCustomId(player, ID_DESERT)
            && hasCustomId(player, ID_CAVE);
    }

    private boolean hasCustomId(ServerPlayerEntity player, String requiredId) {
        if (player == null || requiredId == null) {
            return false;
        }

        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack st = player.getInventory().getStack(slot);
            if (matchesId(st, requiredId)) {
                return true;
            }
        }

        ItemStack off = player.getOffHandStack();
        return matchesId(off, requiredId);
    }

    private boolean matchesId(ItemStack stack, String requiredId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }

        NbtCompound nbt = custom.copyNbt();
        String id = nbt.getString(KEY_ID).orElse("");
        return requiredId.equals(id);
    }

    private static String normalizeCommand(String configured) {
        if (configured == null) {
            return null;
        }
        String trimmed = configured.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("/")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private record Stage1State(long teleportAtTick, ConsumedItems consumed) {
    }

    private record ConsumedItems(ItemStack ocean, ItemStack sky, ItemStack desert, ItemStack cave) {
    }

    private record ZoneState(long enteredAtTick, boolean notWorthyShown, long lastEligibilityCheckTick) {
    }
}
