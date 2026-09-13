package com.silver.babylon;

import com.silver.babylon.config.BabylonConfig;
import com.silver.babylon.config.BabylonConfigManager;
import com.silver.portalprotocol.PortalRequestPayload;
import com.silver.portalprotocol.PortalRequestPayloadCodec;
import com.silver.portalprotocol.PortalRequestSigner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
            "Babylon loaded region=({}, {}, {})..({}, {}, {}) entryDelaySeconds={} particleSeconds={} targetServer='{}'",
            config.region.minX, config.region.minY, config.region.minZ,
            config.region.maxX, config.region.maxY, config.region.maxZ,
            config.entryDelaySeconds,
            config.particleSeconds,
            config.portalRedirectTargetServer
        );
    }

    private void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }

            if (!(player.level() instanceof ServerLevel world)) {
                continue;
            }

            BlockPos pos = player.blockPosition();
            boolean inRegion = config.region.contains(pos.getX(), pos.getY(), pos.getZ());

            if (!inRegion) {
                ZoneState previous = zone.remove(player.getUUID());
                if (previous != null) {
                    LOGGER.info("{} left region at {}", player.getScoreboardName(), pos);
                }

                if (player.entityTags().contains(TAG_STAGE1_PROCESSING)) {
                    handleLeftRegionWhileProcessing(server, world, player);
                } else {
                    stage1.remove(player.getUUID());
                }
                continue;
            }

            if (!zone.containsKey(player.getUUID())) {
                LOGGER.info("{} entered region at {} (entryDelaySeconds={})", player.getScoreboardName(), pos, config.entryDelaySeconds);
            }

            if (player.entityTags().contains(TAG_STAGE1_PROCESSING)) {
                Stage1State state = stage1.get(player.getUUID());
                if (state == null) {
                    cancelProcessing(player);
                    zone.put(player.getUUID(), new ZoneState(world.getGameTime(), false, 0));
                    continue;
                }

                runParticles(world, player);
                if (world.getGameTime() >= state.teleportAtTick) {
                    finishAndTeleport(server, player);
                }
                continue;
            }

            ZoneState zoneState = zone.computeIfAbsent(player.getUUID(),
                u -> new ZoneState(world.getGameTime(), false, 0));

            long nowTick = world.getGameTime();
            long entryDelayTicks = Math.max(0, config.entryDelaySeconds) * 20L;
            if (nowTick - zoneState.enteredAtTick < entryDelayTicks) {
                continue;
            }

            if (zoneState.lastEligibilityCheckTick != 0
                && nowTick - zoneState.lastEligibilityCheckTick < ELIGIBILITY_CHECK_EVERY_TICKS) {
                continue;
            }
            zoneState = new ZoneState(zoneState.enteredAtTick, zoneState.notWorthyShown, nowTick);
            zone.put(player.getUUID(), zoneState);

            if (hasAllFour(player)) {
                LOGGER.info("{} is worthy; starting processing", player.getScoreboardName());
                startProcessing(world, player);
            } else if (!zoneState.notWorthyShown) {
                LOGGER.info("{} is not worthy; sending message", player.getScoreboardName());
                player.sendSystemMessage(Component.literal(config.notWorthyMessage).withStyle(ChatFormatting.RED));
                zone.put(player.getUUID(), new ZoneState(zoneState.enteredAtTick, true, zoneState.lastEligibilityCheckTick));
            }
        }
    }

    private void startProcessing(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }

        ConsumedItems consumed = consumeFour(player);
        if (consumed == null) {
            return;
        }

        player.addTag(TAG_STAGE1_PROCESSING);
        long nowTick = world.getGameTime();
        long delayTicks = Math.max(1, config.particleSeconds) * 20L;
        stage1.put(player.getUUID(), new Stage1State(nowTick + delayTicks, consumed));
        LOGGER.info("{} processing started; teleport in {}s", player.getScoreboardName(), Math.max(1, config.particleSeconds));
        runParticles(world, player);
    }

    private ConsumedItems consumeFour(ServerPlayer player) {
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

    private ItemStack removeOneByCustomId(ServerPlayer player, String requiredId) {
        if (player == null || requiredId == null) {
            return ItemStack.EMPTY;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack st = player.getInventory().getItem(slot);
            if (matchesId(st, requiredId)) {
                ItemStack taken = st.copy();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                return taken;
            }
        }

        ItemStack off = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (matchesId(off, requiredId)) {
            ItemStack taken = off.copy();
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return taken;
        }

        return ItemStack.EMPTY;
    }

    private boolean tryRestore(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return true;
        }

        // Only restore into inventory; if full, treat as failure so we can still teleport.
        return player.getInventory().add(stack);
    }

    private void cancelProcessing(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.removeTag(TAG_STAGE1_PROCESSING);
        stage1.remove(player.getUUID());
    }

    private void handleLeftRegionWhileProcessing(MinecraftServer server, ServerLevel world, ServerPlayer player) {
        if (server == null || world == null || player == null) {
            return;
        }

        Stage1State state = stage1.get(player.getUUID());
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
        if (world.getGameTime() >= state.teleportAtTick) {
            finishAndTeleport(server, player);
        }
    }

    private void runParticles(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }

        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        world.sendParticles(player, ParticleTypes.PORTAL, true, false, x, y, z, 30, 0.6, 0.8, 0.6, 0.02);
    }

    private void finishAndTeleport(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }

        player.removeTag(TAG_STAGE1_PROCESSING);
        stage1.remove(player.getUUID());
        zone.remove(player.getUUID());

        String playerName = player.getScoreboardName();
        CommandSourceStack source = server.createCommandSourceStack();

        final String targetServer = config.portalRedirectTargetServer;
        final String secret = config.portalRequestSecret;
        String destinationPortalCandidate = config.portalRedirectTargetPortal;
        final String destinationPortal = destinationPortalCandidate != null ? destinationPortalCandidate : "";

        server.execute(() -> {
            try {
                String c1 = "mpdsstage1 " + playerName + " true";
                String c2 = "mpdsremovecustomid " + playerName + " id " + ID_OCEAN;
                String c3 = "mpdsremovecustomid " + playerName + " id " + ID_SKY;
                String c4 = "mpdsremovecustomid " + playerName + " id " + ID_DESERT;
                String c5 = "mpdsremovecustomid " + playerName + " id " + ID_CAVE;

                LOGGER.info("Running stage1 commands for {}: '{}', '{}', '{}', '{}', '{}'", playerName, c1, c2, c3, c4, c5);
                server.getCommands().performPrefixedCommand(source, c1);
                server.getCommands().performPrefixedCommand(source, c2);
                server.getCommands().performPrefixedCommand(source, c3);
                server.getCommands().performPrefixedCommand(source, c4);
                server.getCommands().performPrefixedCommand(source, c5);

                if (targetServer == null || targetServer.isBlank()) {
                    LOGGER.warn("Target server is blank; skipping redirect for {}", playerName);
                } else if (secret == null || secret.isBlank()) {
                    LOGGER.warn("portalRequestSecret is blank; cannot send portal request for {}", playerName);
                } else {
                    long issuedAtMs = System.currentTimeMillis();
                    String nonce = PortalRequestPayloadCodec.generateNonce();
                    byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(player.getUUID(), targetServer, destinationPortal, issuedAtMs, nonce);
                    byte[] signature = PortalRequestSigner.hmacSha256(secret, unsigned);
                    byte[] signed = PortalRequestPayloadCodec.encodeSigned(player.getUUID(), targetServer, destinationPortal, issuedAtMs, nonce, signature);
                    ServerPlayNetworking.send(player, new PortalRequestPayload(signed));
                    LOGGER.info("Sent Babylon portal request for {} -> {}", playerName, targetServer);
                }

                LOGGER.info("{} stage1 done", playerName);
            } catch (Exception e) {
                LOGGER.error("Stage1 teleport failed for {}", playerName, e);
            }
        });
    }

    private boolean hasAllFour(ServerPlayer player) {
        return hasCustomId(player, ID_OCEAN)
            && hasCustomId(player, ID_SKY)
            && hasCustomId(player, ID_DESERT)
            && hasCustomId(player, ID_CAVE);
    }

    private boolean hasCustomId(ServerPlayer player, String requiredId) {
        if (player == null || requiredId == null) {
            return false;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack st = player.getInventory().getItem(slot);
            if (matchesId(st, requiredId)) {
                return true;
            }
        }

        ItemStack off = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        return matchesId(off, requiredId);
    }

    private boolean matchesId(ItemStack stack, String requiredId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }

        CompoundTag nbt = custom.copyTag();
        String id = nbt.getStringOr(KEY_ID, "");
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
