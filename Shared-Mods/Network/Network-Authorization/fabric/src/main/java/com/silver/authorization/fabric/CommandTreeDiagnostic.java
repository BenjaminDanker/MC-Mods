package com.silver.authorization.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Temporary console-only read-only dump of the command paths usable by a live player. */
final class CommandTreeDiagnostic {
    private CommandTreeDiagnostic() { }

    static void register(Logger logger) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher, logger));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, Logger logger) {
        dispatcher.register(Commands.literal("auth-tree-dump")
                .requires(AuthorizationChecks::isDedicatedConsole)
                .then(Commands.argument("uuid", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(context -> dump(context.getSource(), dispatcher, logger,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "uuid")))));
    }

    private static int dump(CommandSourceStack caller, CommandDispatcher<CommandSourceStack> dispatcher,
                            Logger logger, String rawUuid) {
        final UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException invalid) {
            caller.sendFailure(Component.literal("Expected an online player's UUID."));
            return 0;
        }
        ServerPlayer player = caller.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            caller.sendFailure(Component.literal("That UUID is not currently online on this backend."));
            return 0;
        }

        CommandSourceStack playerSource = player.createCommandSourceStack();
        List<String> paths = new ArrayList<>();
        for (CommandNode<CommandSourceStack> child : dispatcher.getRoot().getChildren()) {
            collect(child, playerSource, "/", true, paths);
        }
        paths.sort(Comparator.naturalOrder());

        String server = NetworkAuthorizationMod.serverId().value().replaceAll("[^a-z0-9._-]", "_");
        Path output = FabricLoader.getInstance().getGameDir().resolve("logs")
                .resolve("command-tree-" + server + "-" + uuid + ".txt");
        List<String> report = new ArrayList<>();
        report.add("server=" + server + " subject=" + uuid + " username=" + player.getGameProfile().name());
        report.add("captured_at=" + Instant.now());
        report.add("permission_level=" + playerSource.permissions());
        report.add("status path: VISIBLE means every node on the path passes the actual player source predicate; FILTERED means at least one does not.");
        report.addAll(paths);
        try {
            Files.createDirectories(output.getParent());
            Files.write(output, report, StandardCharsets.UTF_8);
            caller.sendSuccess(() -> Component.literal("Captured " + paths.size() + " runtime paths to " + output.getFileName()), false);
            logger.info("[CommandTreeDiagnostic] Captured {} runtime command paths for {} on {} at {}",
                    paths.size(), uuid, server, output.getFileName());
            return paths.size();
        } catch (IOException failure) {
            caller.sendFailure(Component.literal("Could not write command tree dump: " + failure.getMessage()));
            logger.error("[CommandTreeDiagnostic] Could not write command tree dump for {}", uuid, failure);
            return 0;
        }
    }

    private static void collect(CommandNode<CommandSourceStack> node, CommandSourceStack source,
                                String parent, boolean inherited, List<String> lines) {
        boolean visible = inherited && node.canUse(source);
        String token = node instanceof LiteralCommandNode<?> ? node.getName() : "<" + node.getName() + ">";
        String path = parent.equals("/") ? "/" + token : parent + " " + token;
        lines.add((visible ? "VISIBLE " : "FILTERED ")
                + (node.getCommand() == null ? "NODE " : "EXECUTABLE ") + path);
        if (parent.equals("/") && node instanceof LiteralCommandNode<?>
                && List.of("spark", "polymer", "spawn", "seed", "version").contains(node.getName())) {
            var indexedPath = CommandPolicyRuntime.path(node);
            if (indexedPath.isEmpty()) {
                lines.add("POLICY-TRACE " + path + " index=MISSING nodeCanUse=" + node.canUse(source));
            } else {
                var decision = CommandPolicyRuntime.decide(source, indexedPath.orElseThrow());
                lines.add("POLICY-TRACE " + path + " index=" + indexedPath.orElseThrow()
                        + " classification=" + decision.classification()
                        + " permission=" + decision.requiredPermission().map(Object::toString).orElse("-")
                        + " evaluatorVisible=" + decision.visible()
                        + " evaluatorAllowed=" + decision.allowed()
                        + " nodeCanUse=" + node.canUse(source));
            }
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            collect(child, source, path, visible, lines);
        }
    }
}
