package com.silver.elderguardianfight.teleport;

import com.silver.elderguardianfight.ElderGuardianFightMod;
import com.silver.elderguardianfight.config.ConfigManager;
import com.silver.elderguardianfight.config.ElderGuardianControlConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;

/**
 * Handles teleporting nearby players after the Elder Guardian dies.
 */
public final class GuardianRedirector {
    private static final int HALF_RANGE = 10;

    private GuardianRedirector() {
    }

    public static void handleGuardianDeath(ServerWorld world, BlockPos deathPos) {
        ConfigManager manager = ElderGuardianFightMod.getConfigManager();
        if (manager == null) {
            ElderGuardianFightMod.LOGGER.warn("Guardian death redirect skipped because config manager was not initialised");
            return;
        }

        ElderGuardianControlConfig config = manager.getConfig();
        if (!config.portalRedirectEnabled()) {
            ElderGuardianFightMod.LOGGER.info("Guardian redirect disabled via config; no action taken at {}", deathPos);
            return;
        }

        ElderGuardianFightMod.LOGGER.info("Elder Guardian died at {} in world {}; evaluating nearby players", deathPos, world.getRegistryKey().getValue());

        String command = normalizeCommand(config.portalRedirectCommand());
        if (command == null) {
            ElderGuardianFightMod.LOGGER.warn("Guardian redirect command missing; skipping redirect");
            return;
        }

        List<ServerPlayerEntity> targets = world.getPlayers(isWithinSquare(deathPos));
        ElderGuardianFightMod.LOGGER.info("Found {} eligible players within {} blocks of {}", targets.size(), HALF_RANGE, deathPos);
        if (targets.isEmpty()) {
            ElderGuardianFightMod.LOGGER.info("No players within {} blocks of {} when Elder Guardian died", HALF_RANGE, deathPos);
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

            player.sendMessage(Text.literal("Elder Guardian defeated! Redirecting you to " + targetName + "..."), false);
            ServerPlayerEntity commandPlayer = player;
            ElderGuardianFightMod.LOGGER.info("Queueing guardian redirect for {} using '{}'", commandPlayer.getName().getString(), finalCommand);
            server.execute(() -> {
                try {
                    ElderGuardianFightMod.LOGGER.info("Executing guardian redirect command '{}' for {}", finalCommand, commandPlayer.getName().getString());
                    server.getCommandManager().executeWithPrefix(commandPlayer.getCommandSource(), finalCommand);
                } catch (Exception ex) {
                    ElderGuardianFightMod.LOGGER.error("Error executing guardian redirect command '{}'", finalCommand, ex);
                }
            });
        }
    }

    private static Predicate<ServerPlayerEntity> isWithinSquare(BlockPos center) {
        return player -> {
            if (player.isSpectator()) {
                return false;
            }
            int dx = Math.abs(player.getBlockX() - center.getX());
            int dz = Math.abs(player.getBlockZ() - center.getZ());
            int dy = Math.abs(player.getBlockY() - center.getY());
            return dx <= HALF_RANGE && dz <= HALF_RANGE && dy <= HALF_RANGE;
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
