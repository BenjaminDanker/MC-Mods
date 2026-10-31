package com.silver.babylon;

import com.silver.babylon.config.BabylonConfig;
import com.silver.babylon.config.BabylonConfigManager;
import com.silver.portalprotocol.PortalRequestPayload;
import com.silver.portalprotocol.PortalRequestPayloadCodec;
import com.silver.portalprotocol.PortalRequestSigner;
import com.silver.portalprotocol.PortalPetTransferGate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BabylonMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("babylon");

    private static final String TAG_STAGE1_PROCESSING = "babylon_stage1_processing";
    private static final String TAG_STAGE1_DONE = "babylon_stage1_done";
    /** Persistent record of the path a player chose; the other path stays closed to them. */
    private static final String TAG_PATH_PREFIX = "babylon_path_";

    private static final String KEY_ID = "id";

    private static final String ID_OCEAN = "special_sea_lantern";
    private static final String ID_SKY = "special_arrow";
    private static final String ID_DESERT = "special_dead_bush";
    private static final String ID_CAVE = "special_coal_ore";

    private static final long ELIGIBILITY_CHECK_EVERY_TICKS = 10L;

    private static final Map<UUID, ZoneState> zone = new ConcurrentHashMap<>();
    private static final Map<UUID, Stage1State> stage1 = new ConcurrentHashMap<>();
    private static final Set<UUID> hinted = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> commandsShown = ConcurrentHashMap.newKeySet();

    private BabylonConfig config;

    @Override
    public void onInitialize() {
        this.config = new BabylonConfigManager().loadOrCreate();

        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        // /choose exists only for players standing inside the Babylon region (the command tree is
        // re-sent whenever they enter or leave), so nobody else can see or run it.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (config.choices.isEmpty()) {
                return;
            }
            var root = Commands.literal("choose")
                .requires(source -> source.getPlayer() != null && insideRegion(source.getPlayer()));
            for (BabylonConfig.Choice choice : config.choices) {
                root.then(Commands.literal(choice.id)
                    .executes(context -> {
                        choose(context.getSource().getPlayer(), choice, false);
                        return 1;
                    })
                    .then(Commands.literal("confirm").executes(context -> {
                        choose(context.getSource().getPlayer(), choice, true);
                        return 1;
                    })));
            }
            dispatcher.register(root);
        });

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

            if (inRegion ? commandsShown.add(player.getUUID()) : commandsShown.remove(player.getUUID())) {
                server.getCommands().sendCommands(player);
            }

            if (!inRegion) {
                hinted.remove(player.getUUID());
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
                if (config.choices.isEmpty()) {
                    LOGGER.info("{} is worthy; starting processing", player.getScoreboardName());
                    startProcessing(world, player, null);
                } else {
                    BabylonConfig.Choice already = chosenChoice(player);
                    if (already != null) {
                        // The path is permanent: a returning worthy player only ever goes to their own.
                        LOGGER.info("{} is worthy and already chose '{}'; starting processing",
                            player.getScoreboardName(), already.id);
                        startProcessing(world, player, already);
                    } else if (hinted.add(player.getUUID())) {
                        sendChoicePrompt(player);
                    }
                }
            } else if (!zoneState.notWorthyShown) {
                BabylonConfig.Choice walking = config.choices.isEmpty() ? null : chosenChoice(player);
                if (walking != null) {
                    sendSwitchInfo(player, walking);
                } else {
                    LOGGER.info("{} is not worthy; sending message", player.getScoreboardName());
                    player.sendSystemMessage(Component.literal(config.notWorthyMessage).withStyle(ChatFormatting.RED));
                }
                zone.put(player.getUUID(), new ZoneState(zoneState.enteredAtTick, true, zoneState.lastEligibilityCheckTick));
            }
        }
    }

    /** The id of the path this player chose earlier, or null. */
    private static String chosenPath(ServerPlayer player) {
        for (String tag : player.entityTags()) {
            if (tag.startsWith(TAG_PATH_PREFIX)) {
                return tag.substring(TAG_PATH_PREFIX.length());
            }
        }
        return null;
    }

    private BabylonConfig.Choice chosenChoice(ServerPlayer player) {
        String id = chosenPath(player);
        if (id == null) {
            return null;
        }
        for (BabylonConfig.Choice choice : config.choices) {
            if (choice.id.equals(id)) {
                return choice;
            }
        }
        return null;
    }

    /** Shown once to someone who already chose, when they step into the square. */
    private void sendSwitchInfo(ServerPlayer player, BabylonConfig.Choice walking) {
        player.sendSystemMessage(Component.literal("You walk the path of " + labelOf(walking.id) + ".").withStyle(ChatFormatting.AQUA));
        for (BabylonConfig.Choice other : config.choices) {
            if (!other.id.equals(walking.id)) {
                player.sendSystemMessage(Component.literal("  /choose " + other.id + " - switch to " + labelOf(other.id)
                    + " (you will be asked to confirm)").withStyle(ChatFormatting.GRAY));
            }
        }
        player.sendSystemMessage(Component.literal("  /choose " + walking.id + " - go back to " + labelOf(walking.id))
            .withStyle(ChatFormatting.GRAY));
    }

    private void sendChoicePrompt(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(config.chooseHeader).withStyle(ChatFormatting.GOLD));
        for (BabylonConfig.Choice choice : config.choices) {
            player.sendSystemMessage(Component.literal("  /choose " + choice.id).withStyle(ChatFormatting.AQUA));
        }
        if (!config.chooseFooter.isBlank()) {
            player.sendSystemMessage(Component.literal(config.chooseFooter).withStyle(ChatFormatting.RED));
        }
    }

    private boolean insideRegion(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return config != null && config.region.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * /choose <path>: only reachable inside the region. The first choice costs the four boss items. Choosing the path
     * you are already on just sends you there. Choosing the other one asks first, and on confirm erases the old path.
     */
    private void choose(ServerPlayer player, BabylonConfig.Choice choice, boolean confirmed) {
        if (player == null || !(player.level() instanceof ServerLevel world) || !insideRegion(player)) {
            return;
        }
        if (player.entityTags().contains(TAG_STAGE1_PROCESSING)) {
            return;
        }
        BabylonConfig.Choice already = chosenChoice(player);
        if (already != null && !already.id.equals(choice.id)) {
            if (confirmed) {
                switchPath(player, already, choice);
            } else {
                player.openDialog(net.minecraft.core.Holder.direct(
                    SwitchDialog.confirm(labelOf(already.id), labelOf(choice.id), choice.id)));
            }
            return;
        }
        if (already != null) {
            player.sendSystemMessage(Component.literal("You already walk the path of " + labelOf(already.id) + ".")
                .withStyle(ChatFormatting.AQUA));
            redirect(player, already);
            return;
        }
        if (!hasAllFour(player)) {
            player.sendSystemMessage(Component.literal(config.notWorthyMessage).withStyle(ChatFormatting.RED));
            return;
        }
        LOGGER.info("{} chose '{}'", player.getScoreboardName(), choice.id);
        hinted.remove(player.getUUID());
        startProcessing(world, player, choice);
    }

    /** Moves the player to the other path: records it in the database (the old path's server wipes itself later), then sends them. */
    private void switchPath(ServerPlayer player, BabylonConfig.Choice from, BabylonConfig.Choice to) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String name = player.getScoreboardName();
        String uuid = player.getStringUUID();
        java.util.UUID id = player.getUUID();
        PlayerSyncProgress.IO.execute(() -> {
            boolean recorded;
            try {
                PlayerSyncProgress.switchBranch(name, uuid, from.id, to.id);
                recorded = true;
            } catch (Exception failure) {
                LOGGER.error("ALERT: branch switch {} -> {} failed for {}", from.id, to.id, name, failure);
                recorded = false;
            }
            boolean ok = recorded;
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online == null) {
                    return;
                }
                if (!ok) {
                    online.sendSystemMessage(Component.literal("The switch could not be recorded. Nothing was changed; try again in a moment.")
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                online.removeTag(TAG_PATH_PREFIX + from.id);
                online.addTag(TAG_PATH_PREFIX + to.id);
                LOGGER.info("{} switched from '{}' to '{}'", name, from.id, to.id);
                online.sendSystemMessage(Component.literal("Your old path is erased. You now walk the path of " + labelOf(to.id) + ".")
                    .withStyle(ChatFormatting.GOLD));
                redirect(online, to);
            });
        });
    }

    /** Sends a signed request to the proxy to move the player to a path's server. */
    private void redirect(ServerPlayer player, BabylonConfig.Choice choice) {
        String secret = com.silver.portalprotocol.PortalRequestSecret.resolve(config.portalRequestSecret);
        if (choice.targetServer == null || choice.targetServer.isBlank() || secret == null || secret.isBlank()) {
            LOGGER.warn("Cannot redirect {}: target server or secret is blank", player.getScoreboardName());
            return;
        }
        PortalPetTransferGate.send(player, choice.targetServer, choice.targetPortal == null ? "" : choice.targetPortal, secret);
    }

    private String labelOf(String id) {
        for (BabylonConfig.Choice choice : config.choices) {
            if (choice.id.equals(id)) {
                return choice.label.isBlank() ? choice.id : choice.label;
            }
        }
        return id;
    }

    private void startProcessing(ServerLevel world, ServerPlayer player, BabylonConfig.Choice choice) {
        if (world == null || player == null) {
            return;
        }

        ConsumedItems consumed = consumeFour(player);
        if (consumed == null) {
            return;
        }

        player.addTag(TAG_STAGE1_PROCESSING);
        long nowTick = world.getGameTime();
        // The ascension scene (Villager-Interface's cutscenes, called by name): the player looks up and rises into the
        // sky; the transfer waits until it has played (sneaking skips it, the timer still runs).
        int sceneTicks = playAscension(player);
        long delayTicks = Math.max(Math.max(1, config.particleSeconds) * 20L, sceneTicks);
        stage1.put(player.getUUID(), new Stage1State(nowTick + delayTicks, consumed, choice));
        if (choice != null) {
            player.sendSystemMessage(Component.literal("You chose " + (choice.label.isBlank() ? choice.id : choice.label) + ".")
                .withStyle(ChatFormatting.AQUA));
        }
        LOGGER.info("{} processing started; teleport in {}s", player.getScoreboardName(), Math.max(1, config.particleSeconds));
        runParticles(world, player);
    }

    private static int playAscension(ServerPlayer player) {
        try {
            Class<?> scenes = Class.forName("com.silver.villagerinterface.cutscene.Cutscenes");
            return (int) scenes.getMethod("play", ServerPlayer.class, String.class, double.class, double.class, double.class, float.class)
                .invoke(null, player, "babylon_ascension", player.getX(), player.getY(), player.getZ(), player.getYRot());
        } catch (ReflectiveOperationException notHere) {
            return 0;
        }
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
        Stage1State done = stage1.remove(player.getUUID());
        BabylonConfig.Choice chosen = done == null ? null : done.choice();
        zone.remove(player.getUUID());
        hinted.remove(player.getUUID());
        if (chosen != null) {
            player.addTag(TAG_PATH_PREFIX + chosen.id);
        }

        String playerName = player.getScoreboardName();
        String playerUuid = player.getStringUUID();
        java.util.UUID playerId = player.getUUID();

        final String targetServer = chosen != null ? chosen.targetServer : config.portalRedirectTargetServer;
        final String secret = com.silver.portalprotocol.PortalRequestSecret.resolve(config.portalRequestSecret);
        String destinationPortalCandidate = chosen != null ? chosen.targetPortal : config.portalRedirectTargetPortal;
        final String destinationPortal = destinationPortalCandidate != null ? destinationPortalCandidate : "";

        // Record stage 1 and drop the four boss items from the stored inventories (database, so off the server
        // thread), then send the player on from the server thread.
        PlayerSyncProgress.IO.execute(() -> {
            try {
                PlayerSyncProgress.setStage1Cleared(playerName, playerUuid, true);
                int removed = 0;
                for (String bossItem : new String[] {ID_OCEAN, ID_SKY, ID_DESERT, ID_CAVE}) {
                    removed += PlayerSyncProgress.removeStoredItems(playerUuid, "id", bossItem);
                }
                LOGGER.info("{} stage 1 recorded; {} stored boss item(s) removed", playerName, removed);
            } catch (Exception e) {
                LOGGER.error("ALERT: recording stage 1 failed for {}", playerName, e);
            }
            server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
            if (onlinePlayer == null) {
                return;
            }
            try {

                if (targetServer == null || targetServer.isBlank()) {
                    LOGGER.warn("Target server is blank; skipping redirect for {}", playerName);
                } else if (secret == null || secret.isBlank()) {
                    LOGGER.warn("portalRequestSecret is blank; cannot send portal request for {}", playerName);
                } else {
                    if (PortalPetTransferGate.send(onlinePlayer, targetServer, destinationPortal, secret))
                        LOGGER.info("Queued Babylon redirect for {} -> {} after pet preparation", playerName, targetServer);
                }

                LOGGER.info("{} stage1 done", playerName);
            } catch (Exception e) {
                LOGGER.error("Stage1 teleport failed for {}", playerName, e);
            }
            });
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

    private record Stage1State(long teleportAtTick, ConsumedItems consumed, BabylonConfig.Choice choice) {
    }

    private record ConsumedItems(ItemStack ocean, ItemStack sky, ItemStack desert, ItemStack cave) {
    }

    private record ZoneState(long enteredAtTick, boolean notWorthyShown, long lastEligibilityCheckTick) {
    }
}
