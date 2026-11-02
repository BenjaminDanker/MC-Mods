package com.silver.routing.plugin;

import com.silver.routing.api.Routing;
import com.silver.routing.config.ReturnSpecial;
import com.silver.authorization.PermissionNodes;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Centralises registration and handling for all player-routing commands.
 */
public class CommandRegistrar {
    private static final String WAKEUPLOBBY_USAGE = "§7Usage: /wakeuplobby reload";

    private final ProxyServer proxy;
    private final RuntimeState runtime;
    private final PlayerRoutingPlugin plugin;
    private final Logger logger;

    private boolean registered;

    public CommandRegistrar(ProxyServer proxy,
                            RuntimeState runtime,
                            PlayerRoutingPlugin plugin,
                            Logger logger) {
        this.proxy = proxy;
        this.runtime = runtime;
        this.plugin = plugin;
        this.logger = logger;
    }

    void register() {
        if (registered) {
            return;
        }
        registerReloadCommand();
        registerMessageCommands();
        registerServerOverride();
        registerForceServerCommand();
        registerReturnCommand();
        registered = true;
        logger.info("[PlayerRouting] Commands registered: /wakeuplobby reload, /server (override), /forceserver, /return");
    }

    private void registerReloadCommand() {
        proxy.getCommandManager().register(
            "wakeuplobby",
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    CommandSource source = invocation.source();
                    String[] args = invocation.arguments();

                    if (args.length == 0) {
                        source.sendMessage(Component.text(WAKEUPLOBBY_USAGE));
                        return;
                    }

                    if (args[0].equalsIgnoreCase("reload")) {
                        if (!canManageWakeupLobby(source)) {
                            source.sendMessage(Component.text("§cYou do not have permission to use this command."));
                            return;
                        }
                        try {
                            plugin.reloadConfig();
                            source.sendMessage(Component.text("§aPlayer-routing config and velocity ops reloaded successfully."));
                        } catch (IOException e) {
                            logger.error("Reload failed", e);
                            source.sendMessage(Component.text("§cFailed to reload config: " + e.getMessage()));
                        }
                        return;
                    }

                    if (args[0].equalsIgnoreCase("ops")) {
                        handleOpsCommand(source, args);
                        return;
                    }

                    source.sendMessage(Component.text(WAKEUPLOBBY_USAGE));
                }

                @Override
                public boolean hasPermission(Invocation invocation) {
                    return canManageWakeupLobby(invocation.source());
                }
            }
        );
    }

    private void handleOpsCommand(CommandSource source, String[] args) {
        source.sendMessage(Component.text("Legacy Velocity operator management is retired. Use /auth user <player|uuid> role add|remove <role>."));
    }

    private boolean canManageWakeupLobby(CommandSource source) {
        if (source instanceof ConsoleCommandSource) return true;
        return source instanceof Player player
                && plugin.hasAuthorizationPermission(player, PermissionNodes.WAKEUPLOBBY_MANAGE);
    }

    private boolean canForceServer(CommandSource source) {
        if (source instanceof ConsoleCommandSource) return true;
        return source instanceof Player player
                && plugin.hasAuthorizationPermission(player, PermissionNodes.SERVER_SWITCH_FORCE);
    }

    private void registerMessageCommands() {
    // Keep /msg as the sole public private-message entry point. /w and /teammsg
    // remain vanilla backend commands, where the centralized command policy gates
    // them for PLAYER users without removing the commands from Minecraft.
    proxy.getCommandManager().register("msg",
        new SanitizedForwardCommand(proxy, logger, this::hasBypass,
                player -> plugin.hasAuthorizationPermission(player, PermissionNodes.WAKEUPLOBBY_SELECTORS),
                "minecraft:msg", "msg", true));
    }

    private void registerServerOverride() {
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("server")
                        .aliases("connect")
                        .build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        if (!(invocation.source() instanceof Player player)) {
                            invocation.source().sendMessage(Component.text("Players only."));
                            return;
                        }

                        if (!hasBypass(player)) {
                            player.sendMessage(Component.text("⚠ Server switching is disabled."));
                            return;
                        }

                        String[] args = invocation.arguments();
                        if (args.length == 0) {
                            player.sendMessage(Component.text("Usage: /server <server>"));
                            return;
                        }

                        String targetServer = args[0];
                        var serverOpt = proxy.getServer(targetServer);
                        if (serverOpt.isEmpty()) {
                            player.sendMessage(Component.text("⚠ Unknown server: " + targetServer));
                            return;
                        }

                        // Cancels any in-progress sticky move, then connects, or waits in the lobby while an offline server starts.
                        plugin.adminConnect(player, serverOpt.get());
                    }

                    @Override
                    public boolean hasPermission(Invocation invocation) {
                        return invocation.source() instanceof Player player && hasBypass(player);
                    }

                    @Override
                    public java.util.List<String> suggest(Invocation invocation) {
                        String[] args = invocation.arguments();
                        if (args.length > 1) {
                            return java.util.List.of();
                        }
                        String prefix = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
                        return proxy.getAllServers().stream()
                                .map(server -> server.getServerInfo().getName())
                                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                                .sorted()
                                .collect(java.util.stream.Collectors.toList());
                    }
                }
        );
    }

    private void registerForceServerCommand() {
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("forceserver").build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        CommandSource source = invocation.source();
                        if (!canForceServer(source)) {
                            source.sendMessage(Component.text("§cYou do not have permission to use this command."));
                            return;
                        }

                        String[] args = invocation.arguments();
                        if (args.length < 2) {
                            source.sendMessage(Component.text("§7Usage: /forceserver <player> <server>"));
                            return;
                        }

                        String targetPlayerName = args[0];
                        String targetServerName = args[1];

                        Optional<Player> targetPlayerOpt = proxy.getPlayer(targetPlayerName);
                        if (targetPlayerOpt.isEmpty()) {
                            source.sendMessage(Component.text("§cPlayer '" + targetPlayerName + "' is not online."));
                            return;
                        }

                        var serverOpt = proxy.getServer(targetServerName);
                        if (serverOpt.isEmpty()) {
                            source.sendMessage(Component.text("§cUnknown server: " + targetServerName));
                            return;
                        }

                        Player targetPlayer = targetPlayerOpt.get();

                        // Exactly like the /server override: connect, or wait in the lobby while an offline server starts
                        plugin.adminConnect(targetPlayer, serverOpt.get());
                        source.sendMessage(Component.text("§aMoving " + targetPlayerName + " to " + targetServerName + "..."));
                    }

                    @Override
                    public boolean hasPermission(Invocation invocation) {
                        return canForceServer(invocation.source());
                    }

                    @Override
                    public java.util.List<String> suggest(Invocation invocation) {
                        String[] args = invocation.arguments();
                        if (args.length == 0 || args.length == 1) {
                            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
                            return proxy.getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .collect(java.util.stream.Collectors.toList());
                        } else if (args.length == 2) {
                            String prefix = args[1].toLowerCase();
                            return proxy.getAllServers().stream()
                                    .map(server -> server.getServerInfo().getName())
                                    .filter(name -> name.toLowerCase().startsWith(prefix))
                                    .collect(java.util.stream.Collectors.toList());
                        }
                        return java.util.List.of();
                    }
                }
        );
    }

    private void registerReturnCommand() {
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("return").build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        if (!(invocation.source() instanceof Player player)) {
                            invocation.source().sendMessage(Component.text("Players only."));
                            return;
                        }

                        String current = player.getCurrentServer()
                                .map(cs -> cs.getServerInfo().getName())
                                .orElse(null);
                        if (current == null || !current.equalsIgnoreCase(runtime.holdingServer())) {
                            player.sendMessage(Component.text("⚠ You can only use /return while waiting in the lobby."));
                            return;
                        }

                        UUID playerId = player.getUniqueId();

                        // A fresh arrival waiting on an offline server is handled by the waiting-lobby plugin.
                        if (Routing.handleOfflineReturn(player)) {
                            return;
                        }

                        // Existing backend -> lobby -> backend sticky waits keep the original
                        // return behavior exactly as before.
                        if (!runtime.stickyRouter().isReturnEligible(playerId)) {
                            player.sendMessage(Component.text("⚠ /return is only available after the grace period expires."));
                            return;
                        }

                        String dest = plugin.computeReturnDestination(playerId);
                        if (dest == null || dest.isBlank()) {
                            player.sendMessage(Component.text("⚠ No return destination is configured."));
                            return;
                        }

                        var serverOpt = proxy.getServer(dest);
                        if (serverOpt.isEmpty()) {
                            player.sendMessage(Component.text("⚠ Return server not found: " + dest));
                            return;
                        }

                        var specialsToRemove = plugin.computeReturnSpecials(player.getUniqueId());
                        player.sendMessage(Component.text("§eReturning you to §a" + dest + "§e…"));
                        String returnOrigin = runtime.stickyRouter().returnOriginServer(player.getUniqueId());

                        // If we have specials to remove, do it BEFORE switching servers and confirm they're gone.
                        if (!specialsToRemove.isEmpty()) {
                            player.sendMessage(Component.text("§eRemoving special items before return…"));
                            var itemReturn = Routing.itemReturn();
                            if (itemReturn.isEmpty()) {
                                player.sendMessage(Component.text("⚠ Could not confirm special item removal. Not returning."));
                                return;
                            }
                            itemReturn.get().requestItemReturn(player, specialsToRemove).whenComplete((resp, ex) -> {
                                if (ex != null || resp == null || !resp.success()) {
                                    player.sendMessage(Component.text("⚠ Could not confirm special item removal. Not returning."));
                                    return;
                                }
                                if (resp.remainingInventory() > 0 || resp.remainingDb() > 0) {
                                    player.sendMessage(Component.text("⚠ Special items still present after removal. Not returning."));
                                    return;
                                }

                                // Now that removal is confirmed, consume eligibility so it can't be spammed.
                                runtime.stickyRouter().clearReturnEligibility(player.getUniqueId());

                                // Manual return should cancel any in-progress sticky auto-move sequence.
                                runtime.stickyRouter().cancelStickyWait(player.getUniqueId());
                                plugin.cancelPortalTransfer(player.getUniqueId());
                                runtime.stickyRouter().markInternalOnce(player.getUniqueId(), serverOpt.get().getServerInfo().getName());
                                player.createConnectionRequest(serverOpt.get()).connect().whenComplete((result, err) -> {
                                    if (err != null || result == null || !result.isSuccessful()) {
                                        player.sendMessage(Component.text("⚠ Failed to connect to " + dest + "."));
                                        return;
                                    }
                                    plugin.requestReturnOverworldIfNeeded(player, dest, returnOrigin);
                                });
                            });
                            return;
                        }

                        // No specials; return immediately.
                        String returnOriginImmediate = runtime.stickyRouter().returnOriginServer(player.getUniqueId());
                        runtime.stickyRouter().clearReturnEligibility(player.getUniqueId());
                        runtime.stickyRouter().cancelStickyWait(player.getUniqueId());
                        plugin.cancelPortalTransfer(player.getUniqueId());
                        runtime.stickyRouter().markInternalOnce(player.getUniqueId(), serverOpt.get().getServerInfo().getName());
                        player.createConnectionRequest(serverOpt.get()).connect().whenComplete((result, err) -> {
                            if (err != null || result == null || !result.isSuccessful()) {
                                player.sendMessage(Component.text("⚠ Failed to connect to " + dest + "."));
                                return;
                            }
                            plugin.requestReturnOverworldIfNeeded(player, dest, returnOriginImmediate);
                        });
                    }
                }
        );
    }

    private boolean hasBypass(Player player) {
        return plugin.hasAuthorizationPermission(player, PermissionNodes.WAKEUPLOBBY_SERVER);
    }
}
