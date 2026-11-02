package com.silver.chronicle.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.silver.authorization.fabric.AuthorizationChecks;
import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.common.ChronicleProtocol.Operation;
import com.silver.chronicle.common.PrivacyMode;
import com.silver.chronicle.fabric.ChronicleRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ChronicleCommands {
    private static final String STAFF_VIEW = "chronicle.history.staff.view_concealed";
    private static final String ADMIN_REANNOUNCE = "chronicle.history.admin.reannounce";
    private static final String ADMIN_MANAGE = "chronicle.history.admin.manage";

    private ChronicleCommands() { }

    public static void register(ChronicleRuntime runtime) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, runtime));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, ChronicleRuntime runtime) {
        dispatcher.register(Commands.literal("history")
                .then(Commands.literal("firsts")
                        .executes(context -> playerRequest(context.getSource(), runtime, Operation.HISTORY, List.of())))
                .then(Commands.literal("mine")
                        .executes(context -> playerRequest(context.getSource(), runtime, Operation.MINE, List.of())))
                .then(Commands.literal("privacy")
                        .then(Commands.literal("public").executes(context -> setPrivacy(context.getSource(), runtime, PrivacyMode.PUBLIC)))
                        .then(Commands.literal("mysterious").executes(context -> setPrivacy(context.getSource(), runtime, PrivacyMode.MYSTERIOUS)))
                        .then(Commands.literal("anonymous").executes(context -> setPrivacy(context.getSource(), runtime, PrivacyMode.ANONYMOUS)))
                        .then(Commands.literal("secret").executes(context -> setPrivacy(context.getSource(), runtime, PrivacyMode.SECRET))))
                .then(Commands.literal("admin")
                        .requires(source -> AuthorizationChecks.has(source, STAFF_VIEW)
                                || AuthorizationChecks.has(source, ADMIN_REANNOUNCE)
                                || AuthorizationChecks.has(source, ADMIN_MANAGE))
                        .then(Commands.literal("concealed")
                                .requires(source -> AuthorizationChecks.has(source, STAFF_VIEW))
                                .executes(context -> playerRequest(context.getSource(), runtime, Operation.ADMIN_CONCEALED, List.of())))
                        .then(Commands.literal("reannounce")
                                .requires(source -> AuthorizationChecks.has(source, ADMIN_REANNOUNCE))
                                .then(Commands.argument("event_id", StringArgumentType.word())
                                        .suggests(ChronicleCommands::suggestEventIds)
                                        .executes(context -> playerRequest(context.getSource(), runtime, Operation.ADMIN_REANNOUNCE,
                                                List.of(StringArgumentType.getString(context, "event_id"))))))
                        .then(Commands.literal("exclude").requires(source -> AuthorizationChecks.has(source, ADMIN_MANAGE))
                                .then(Commands.argument("player_or_uuid", StringArgumentType.word())
                                        .suggests(ChronicleCommands::suggestPlayers)
                                        .executes(context -> exclude(context.getSource(), runtime,
                                                StringArgumentType.getString(context, "player_or_uuid"), ""))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> exclude(context.getSource(), runtime,
                                                        StringArgumentType.getString(context, "player_or_uuid"),
                                                        StringArgumentType.getString(context, "reason"))))))
                        .then(Commands.literal("include").requires(source -> AuthorizationChecks.has(source, ADMIN_MANAGE))
                                .then(Commands.argument("player_or_uuid", StringArgumentType.word())
                                        .suggests(ChronicleCommands::suggestPlayers)
                                        .executes(context -> include(context.getSource(), runtime,
                                                StringArgumentType.getString(context, "player_or_uuid")))))
                        .then(Commands.literal("exclusions").requires(source -> AuthorizationChecks.has(source, ADMIN_MANAGE))
                                .executes(context -> playerRequest(context.getSource(), runtime, Operation.ADMIN_EXCLUSIONS, List.of())))
                        .then(Commands.literal("reset").requires(source -> AuthorizationChecks.has(source, ADMIN_MANAGE))
                                .then(Commands.literal("event")
                                        .then(Commands.argument("event_id", StringArgumentType.word())
                                                .suggests(ChronicleCommands::suggestEventIds)
                                                .executes(context -> playerRequest(context.getSource(), runtime, Operation.ADMIN_RESET_EVENT,
                                                        List.of(StringArgumentType.getString(context, "event_id")))))))
                        .then(Commands.literal("remove").requires(source -> AuthorizationChecks.has(source, ADMIN_MANAGE))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player_or_uuid", StringArgumentType.word())
                                                .suggests(ChronicleCommands::suggestPlayers)
                                                .executes(context -> removePlayer(context.getSource(), runtime,
                                                        StringArgumentType.getString(context, "player_or_uuid"), null))
                                                .then(Commands.argument("event_id", StringArgumentType.word())
                                                        .suggests(ChronicleCommands::suggestEventIds)
                                                        .executes(context -> removePlayer(context.getSource(), runtime,
                                                                StringArgumentType.getString(context, "player_or_uuid"),
                                                                StringArgumentType.getString(context, "event_id")))))))));
    }

    private static CompletableFuture<Suggestions> suggestEventIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (ChronicleEvent event : ChronicleEvent.values()) {
            if (event.id().startsWith(prefix)) builder.suggest(event.id());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> targets = new ArrayList<>();
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            targets.add(player.getGameProfile().name());
            targets.add(player.getUUID().toString());
        }
        String prefix = builder.getRemainingLowerCase();
        for (String target : targets) {
            if (target.toLowerCase(Locale.ROOT).startsWith(prefix)) builder.suggest(target);
        }
        return builder.buildFuture();
    }

    private static int exclude(CommandSourceStack source, ChronicleRuntime runtime, String rawTarget, String reason) {
        ResolvedTarget target = resolveTarget(source, rawTarget);
        if (target == null) return unknownTarget(source);
        return playerRequest(source, runtime, Operation.ADMIN_EXCLUDE,
                List.of(target.playerId().toString(), target.nameHint(), reason));
    }

    private static int include(CommandSourceStack source, ChronicleRuntime runtime, String rawTarget) {
        ResolvedTarget target = resolveTarget(source, rawTarget);
        if (target == null) return unknownTarget(source);
        return playerRequest(source, runtime, Operation.ADMIN_INCLUDE, List.of(target.playerId().toString()));
    }

    private static int removePlayer(CommandSourceStack source, ChronicleRuntime runtime, String rawTarget, String eventId) {
        ResolvedTarget target = resolveTarget(source, rawTarget);
        if (target == null) return unknownTarget(source);
        return playerRequest(source, runtime, Operation.ADMIN_REMOVE_PLAYER,
                eventId == null ? List.of(target.playerId().toString()) : List.of(target.playerId().toString(), eventId));
    }

    private static ResolvedTarget resolveTarget(CommandSourceStack source, String rawTarget) {
        try {
            return new ResolvedTarget(UUID.fromString(rawTarget), "");
        } catch (IllegalArgumentException ignored) { }
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(rawTarget);
        if (online != null) return new ResolvedTarget(online.getUUID(), online.getGameProfile().name());
        return null;
    }

    private static int unknownTarget(CommandSourceStack source) {
        source.sendFailure(Component.literal("Player is not online or in the profile cache; use their UUID."));
        return 0;
    }

    private static int playerRequest(CommandSourceStack source, ChronicleRuntime runtime, Operation op, List<String> args) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This Chronicle command must be run by a player."));
            return 0;
        }
        runtime.request(player, op, args, ignored -> { });
        return 1;
    }

    private static int setPrivacy(CommandSourceStack source, ChronicleRuntime runtime, PrivacyMode mode) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This Chronicle command must be run by a player."));
            return 0;
        }
        runtime.request(player, Operation.SET_PRIVACY, List.of(mode.id()), ignored -> { });
        return 1;
    }

    private record ResolvedTarget(UUID playerId, String nameHint) { }
}
