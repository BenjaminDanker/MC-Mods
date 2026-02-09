package com.silver.wardenfight.teleport;

import com.silver.wardenfight.WardenFightMod;
import com.silver.wardenfight.config.ConfigManager;
import com.silver.wardenfight.config.WardenControlConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;

/**
 * Handles teleporting nearby players after the Warden dies.
 */
public final class WardenRedirector {
    private WardenRedirector() {
    }

    public static void handleWardenDeath(ServerWorld world, BlockPos deathPos) {
        ConfigManager manager = WardenFightMod.getConfigManager();
        if (manager == null) {
            WardenFightMod.LOGGER.warn("Warden death redirect skipped because config manager was not initialised");
            return;
        }

        WardenControlConfig config = manager.getConfig();
        if (!config.portalRedirectEnabled()) {
            WardenFightMod.LOGGER.info("Warden redirect disabled via config; no action taken at {}", deathPos);
            return;
        }

        int range = Math.max(1, config.portalRedirectRange());
        WardenFightMod.LOGGER.info("Warden died at {} in world {}; evaluating nearby players", deathPos, world.getRegistryKey().getValue());

        String command = normalizeCommand(config.portalRedirectCommand());
        if (command == null) {
            WardenFightMod.LOGGER.warn("Warden redirect command missing; skipping redirect");
            return;
        }

        List<ServerPlayerEntity> targets = world.getPlayers(isWithinSquare(deathPos, range));
        WardenFightMod.LOGGER.info("Found {} eligible players within {} blocks of {}", targets.size(), range, deathPos);
        if (targets.isEmpty()) {
            WardenFightMod.LOGGER.info("No players within {} blocks of {} when Warden died", range, deathPos);
            return;
        }

        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        String finalCommand = command.startsWith("/") ? command : "/" + command;
        String targetName = extractServerName(command);
        if (targetName == null) {
            targetName = "another server";
        }

        for (ServerPlayerEntity player : targets) {
            if (player.isRemoved()) {
                continue;
            }

            player.sendMessage(Text.literal("Warden defeated! Redirecting you to " + targetName + "..."), false);
            ServerPlayerEntity commandPlayer = player;
            WardenFightMod.LOGGER.info("Queueing warden redirect for {} using '{}'", commandPlayer.getName().getString(), finalCommand);
            server.execute(() -> {
                try {
                    WardenFightMod.LOGGER.info("Executing warden redirect command '{}' for {}", finalCommand, commandPlayer.getName().getString());
                    server.getCommandManager().executeWithPrefix(commandPlayer.getCommandSource(), finalCommand);
                } catch (Exception ex) {
                    WardenFightMod.LOGGER.error("Error executing warden redirect command '{}'", finalCommand, ex);
                }
            });
        }
    }

    private static Predicate<ServerPlayerEntity> isWithinSquare(BlockPos center, int range) {
        return player -> {
            if (player.isSpectator()) {
                return false;
            }
            int dx = Math.abs(player.getBlockX() - center.getX());
            int dz = Math.abs(player.getBlockZ() - center.getZ());
            int dy = Math.abs(player.getBlockY() - center.getY());
            return dx <= range && dz <= range && dy <= range;
        };
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
