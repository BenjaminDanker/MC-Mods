package com.silver.networkaccess.plugin;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.velocity.ProxyAuthorization;
import com.silver.authorization.velocity.UUIDResolver;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.UUID;

/** {@code /networkaccess whitelist <list|add|remove> [player]}: edits the proxy whitelist (admission managers and console). */
public final class WhitelistCommand implements SimpleCommand {
    private static final String USAGE = "§7Usage: /networkaccess whitelist <list|add|remove> [player]";

    private final WhitelistStore store;
    private final UUIDResolver uuidResolver;
    private final Logger logger;

    public WhitelistCommand(ProxyServer proxy, WhitelistStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
        this.uuidResolver = new UUIDResolver(proxy, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0 || !args[0].equalsIgnoreCase("whitelist")) {
            source.sendMessage(Component.text(USAGE));
            return;
        }
        handleWhitelistCommand(source, args);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return canManageAdmission(invocation.source());
    }

    private void handleWhitelistCommand(CommandSource source, String[] args) {
        if (!canManageAdmission(source)) {
            source.sendMessage(Component.text("§cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 2) {
            source.sendMessage(Component.text(USAGE));
            return;
        }

        if (args[1].equalsIgnoreCase("list")) {
            var uuids = store.list();
            if (uuids.isEmpty()) {
                source.sendMessage(Component.text("§eWhitelist is empty."));
            } else {
                source.sendMessage(Component.text("§aWhitelist (" + uuids.size() + " player(s)):"));
                // Resolve names asynchronously
                for (UUID uuid : uuids) {
                    uuidResolver.resolveName(uuid).thenAccept(nameOpt -> {
                        String display = nameOpt.map(name -> "§f" + name + "§7 (" + uuid + ")")
                                .orElse("§f" + uuid.toString());
                        source.sendMessage(Component.text("  " + display));
                    });
                }
            }
            return;
        }

        if (args.length < 3) {
            source.sendMessage(Component.text("§7Usage: /networkaccess whitelist " + args[1].toLowerCase() + " <player>"));
            return;
        }

        String target = args[2];

        // Try parsing as UUID first, then as player name
        try {
            UUID uuid = UUID.fromString(target);
            handleWhitelistUUID(source, args[1], uuid, target);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try resolving as player name
            source.sendMessage(Component.text("§eResolving player '" + target + "'..."));
            uuidResolver.resolveUUID(target).thenAccept(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    source.sendMessage(Component.text("§cPlayer '" + target + "' not found. Player may not exist or Mojang API is unavailable."));
                    return;
                }
                handleWhitelistUUID(source, args[1], uuidOpt.get(), target);
            }).exceptionally(ex -> {
                logger.error("Failed to resolve UUID for player '{}'", target, ex);
                source.sendMessage(Component.text("§cFailed to resolve player '" + target + "': " + ex.getMessage()));
                return null;
            });
        }
    }

    private void handleWhitelistUUID(CommandSource source, String action, UUID uuid, String originalInput) {
        try {
            if (action.equalsIgnoreCase("add")) {
                boolean changed = store.add(uuid);
                source.sendMessage(Component.text(changed
                        ? "§aAdded to whitelist: §f" + originalInput + "§7 (" + uuid + ")"
                        : "§eAlready whitelisted: §f" + originalInput + "§7 (" + uuid + ")"));
                return;
            }

            if (action.equalsIgnoreCase("remove")) {
                boolean changed = store.remove(uuid);
                source.sendMessage(Component.text(changed
                        ? "§aRemoved from whitelist: §f" + originalInput + "§7 (" + uuid + ")"
                        : "§eNot in whitelist: §f" + originalInput + "§7 (" + uuid + ")"));
                return;
            }

            source.sendMessage(Component.text(USAGE));
        } catch (IOException e) {
            logger.error("Failed to update whitelist", e);
            source.sendMessage(Component.text("§cFailed updating whitelist: " + e.getMessage()));
        }
    }

    private boolean canManageAdmission(CommandSource source) {
        if (source instanceof ConsoleCommandSource) return true;
        return source instanceof Player player
                && ProxyAuthorization.hasOnCurrentServer(player, PermissionNodes.ADMISSION_MANAGE);
    }
}
