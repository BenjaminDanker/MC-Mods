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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /whitelist [server] [on|off|list|add <player>|remove <player>]} (admission managers and console). Without a
 * server name it is the network whitelist, checked when a player joins; with one it is that server's own whitelist,
 * checked when a player goes there. It replaces the vanilla /whitelist of the servers behind the proxy.
 */
public final class WhitelistCommand implements SimpleCommand {
    private static final String USAGE = "§7Usage: /whitelist [server] <on|off|list|add|remove> [player]";
    private static final List<String> ACTIONS = List.of("on", "off", "list", "add", "remove");

    private final ProxyServer proxy;
    private final Whitelists whitelists;
    private final UUIDResolver uuidResolver;
    private final Logger logger;

    public WhitelistCommand(ProxyServer proxy, Whitelists whitelists, Logger logger) {
        this.proxy = proxy;
        this.whitelists = whitelists;
        this.logger = logger;
        this.uuidResolver = new UUIDResolver(proxy, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!canManageAdmission(source)) {
            source.sendMessage(Component.text("§cYou do not have permission to use this command."));
            return;
        }
        String[] args = invocation.arguments();
        String server = null;
        int at = 0;
        if (args.length > 0 && !ACTIONS.contains(args[0].toLowerCase(Locale.ROOT))) {
            var registered = proxy.getServer(args[0]);
            if (registered.isEmpty()) {
                source.sendMessage(Component.text("§cThere is no server called " + args[0] + "."));
                source.sendMessage(Component.text(USAGE));
                return;
            }
            server = registered.get().getServerInfo().getName();
            at = 1;
        }
        String scope = server == null ? "The network whitelist" : "The " + server + " whitelist";

        if (args.length <= at) {
            source.sendMessage(Component.text(whitelists.isOn(server) ? "§a" + scope + " is on."
                    : "§e" + scope + " is off (anyone may " + (server == null ? "join" : "enter") + ")."));
            source.sendMessage(Component.text(USAGE));
            return;
        }
        String action = args[at].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "on", "off" -> switchWhitelist(source, server, scope, action.equals("on"));
                case "list" -> list(source, whitelists.store(server), scope);
                case "add", "remove" -> {
                    if (args.length <= at + 1) {
                        source.sendMessage(Component.text("§7Usage: /whitelist " + (server == null ? "" : server + " ")
                                + action + " <player>"));
                        return;
                    }
                    change(source, whitelists.store(server), scope, action, args[at + 1]);
                }
                default -> source.sendMessage(Component.text(USAGE));
            }
        } catch (IOException e) {
            logger.error("Failed to update a whitelist", e);
            source.sendMessage(Component.text("§cFailed updating the whitelist: " + e.getMessage()));
        }
    }

    private void switchWhitelist(CommandSource source, String server, String scope, boolean on) throws IOException {
        var file = whitelists.switchFile(server);
        if (on) {
            whitelists.store(server); // creates the folder and an empty list
            if (!Files.exists(file)) Files.createFile(file);
        } else {
            Files.deleteIfExists(file);
        }
        logger.info("[NetworkAccess] {} turned {} by {}", scope, on ? "on" : "off",
                source instanceof Player player ? player.getUsername() : "console");
        source.sendMessage(Component.text("§a" + scope + " is " + (on ? "on: only listed players can "
                + (server == null ? "join." : "enter (and its admins).") : "off.")));
    }

    private void list(CommandSource source, WhitelistStore store, String scope) {
        var uuids = store.list();
        if (uuids.isEmpty()) {
            source.sendMessage(Component.text("§e" + scope + " is empty."));
            return;
        }
        source.sendMessage(Component.text("§a" + scope + " (" + uuids.size() + " player(s)):"));
        for (UUID uuid : uuids) {
            uuidResolver.resolveName(uuid).thenAccept(nameOpt -> source.sendMessage(Component.text("  "
                    + nameOpt.map(name -> "§f" + name + "§7 (" + uuid + ")").orElse("§f" + uuid))));
        }
    }

    private void change(CommandSource source, WhitelistStore store, String scope, String action, String target) {
        try {
            apply(source, store, scope, action, UUID.fromString(target), target);
            return;
        } catch (IllegalArgumentException notAUuid) {
            // a player name: look it up
        }
        source.sendMessage(Component.text("§eResolving player '" + target + "'..."));
        uuidResolver.resolveUUID(target).thenAccept(uuidOpt -> {
            if (uuidOpt.isEmpty()) {
                source.sendMessage(Component.text("§cPlayer '" + target + "' not found. Player may not exist or Mojang API is unavailable."));
                return;
            }
            apply(source, store, scope, action, uuidOpt.get(), target);
        }).exceptionally(ex -> {
            logger.error("Failed to resolve UUID for player '{}'", target, ex);
            source.sendMessage(Component.text("§cFailed to resolve player '" + target + "': " + ex.getMessage()));
            return null;
        });
    }

    private void apply(CommandSource source, WhitelistStore store, String scope, String action, UUID uuid, String input) {
        try {
            boolean add = action.equals("add");
            boolean changed = add ? store.add(uuid) : store.remove(uuid);
            String who = "§f" + input + "§7 (" + uuid + ")";
            source.sendMessage(Component.text(add
                    ? (changed ? "§aAdded to " : "§eAlready on ") + scope.toLowerCase(Locale.ROOT) + ": " + who
                    : (changed ? "§aRemoved from " : "§eNot on ") + scope.toLowerCase(Locale.ROOT) + ": " + who));
        } catch (IOException e) {
            logger.error("Failed to update a whitelist", e);
            source.sendMessage(Component.text("§cFailed updating the whitelist: " + e.getMessage()));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!canManageAdmission(invocation.source())) {
            return List.of();
        }
        String[] args = invocation.arguments();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(ACTIONS);
            proxy.getAllServers().forEach(s -> options.add(s.getServerInfo().getName()));
            return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
        }
        if (args.length == 2 && !ACTIONS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return ACTIONS.stream().filter(o -> o.startsWith(typed)).toList();
        }
        String action = args[args.length - 2].toLowerCase(Locale.ROOT);
        if (action.equals("add") || action.equals("remove")) {
            return proxy.getAllPlayers().stream().map(Player::getUsername)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return canManageAdmission(invocation.source());
    }

    private boolean canManageAdmission(CommandSource source) {
        if (source instanceof ConsoleCommandSource) return true;
        return source instanceof Player player
                && ProxyAuthorization.hasOnCurrentServer(player, PermissionNodes.ADMISSION_MANAGE);
    }
}
