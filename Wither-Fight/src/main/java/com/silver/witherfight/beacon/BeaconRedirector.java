package com.silver.witherfight.beacon;

import com.silver.witherfight.WitherFightMod;
import com.silver.witherfight.config.ConfigManager;
import com.silver.witherfight.config.WitherControlConfig;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.GameEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects when a player stands on an activated beacon, executes the configured redirect command,
 * and removes the beacon that triggered the transfer.
 */
public final class BeaconRedirector {
    private static final long REDIRECT_COOLDOWN_MS = 3_000L;

    private static final Map<UUID, Long> lastRedirects = new ConcurrentHashMap<>();

    private BeaconRedirector() {
    }

    public static void handlePlayer(ServerPlayerEntity player, ServerWorld world) {
        if (player.isSpectator() || player.isRemoved()) {
            return;
        }

        ConfigManager manager = WitherFightMod.getConfigManager();
        if (manager == null) {
            return;
        }

        WitherControlConfig config = manager.getConfig();
        if (!config.portalRedirectEnabled()) {
            return;
        }

        BlockPos beaconPos = findActivatedBeacon(world, player);
        if (beaconPos == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRedirect = lastRedirects.get(player.getUuid());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            return;
        }

        if (!executeRedirect(player, config, world)) {
            return;
        }

        lastRedirects.put(player.getUuid(), now);
        removeBeacon(world, beaconPos, player);
    }

    private static BlockPos findActivatedBeacon(ServerWorld world, ServerPlayerEntity player) {
        BlockPos beaconPos = player.getBlockPos().down();
        BlockState state = world.getBlockState(beaconPos);
        if (!(state.getBlock() instanceof BeaconBlock)) {
            return null;
        }

        BlockEntity blockEntity = world.getBlockEntity(beaconPos);
        if (!(blockEntity instanceof BeaconBlockEntity beaconEntity)) {
            return null;
        }

        if (beaconEntity.getBeamSegments().isEmpty()) {
            return null;
        }

        WitherFightMod.LOGGER.info("Active beacon detected under {} at {}", player.getName().getString(), beaconPos);
        return beaconPos;
    }

    private static boolean executeRedirect(ServerPlayerEntity player, WitherControlConfig config, ServerWorld world) {
        String command = normalizeCommand(config.portalRedirectCommand());
        if (command == null) {
            WitherFightMod.LOGGER.warn("Beacon redirect command missing; skipping redirect for {}", player.getName().getString());
            return false;
        }

        var server = world.getServer();

        String targetName = extractServerName(command);
        if (targetName == null) {
            targetName = "another server";
        }
        player.sendMessage(Text.literal("Redirecting you to " + targetName + "..."), false);

        String finalCommand = command.startsWith("/") ? command : "/" + command;
        server.execute(() -> {
            try {
                WitherFightMod.LOGGER.info("Executing beacon redirect command '{}' for {}", finalCommand, player.getName().getString());
                server.getCommandManager().executeWithPrefix(player.getCommandSource(), finalCommand);
            } catch (Exception ex) {
                WitherFightMod.LOGGER.error("Error executing beacon redirect command '{}'", finalCommand, ex);
            }
        });

        return true;
    }

    private static void removeBeacon(ServerWorld world, BlockPos beaconPos, ServerPlayerEntity player) {
        BlockState state = world.getBlockState(beaconPos);
        if (!(state.getBlock() instanceof BeaconBlock)) {
            return;
        }

        world.setBlockState(beaconPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
        world.emitGameEvent(GameEvent.BLOCK_DESTROY, beaconPos, GameEvent.Emitter.of(player, state));
        WitherFightMod.LOGGER.info("Removed beacon at {} after redirecting {}", beaconPos, player.getName().getString());
    }

    private static String normalizeCommand(String configured) {
        if (configured == null) {
            return null;
        }

        String trimmed = configured.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static String extractServerName(String command) {
        if (command == null || command.isEmpty()) {
            return null;
        }

        int firstQuote = command.indexOf('"');
        if (firstQuote >= 0) {
            int secondQuote = command.indexOf('"', firstQuote + 1);
            if (secondQuote > firstQuote) {
                return extractServerName(command.substring(firstQuote + 1, secondQuote));
            }
        }

        String normalized = command;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        String[] parts = normalized.trim().split("\\s+");
        if (parts.length >= 3 && "wl".equals(parts[0]) && "portal".equals(parts[1])) {
            return parts[2];
        }
        if (parts.length == 1) {
            return parts[0];
        }
        return null;
    }
}
