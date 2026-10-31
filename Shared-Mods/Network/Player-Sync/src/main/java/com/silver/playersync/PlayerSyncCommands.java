package com.silver.playersync;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Staff commands, all under {@code /playersync}. The database work runs on a worker thread; the answer comes back
 * in chat.
 */
final class PlayerSyncCommands {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Player-Sync-Commands");
        thread.setDaemon(true);
        return thread;
    });

    private PlayerSyncCommands() {
    }

    @FunctionalInterface
    private interface Job {
        String run() throws Exception;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var progression = AuthorizationChecks.requires(PermissionNodes.PLAYERSYNC_PROGRESSION_MODIFY);
        dispatcher.register(literal("playersync")
            .then(literal("boss").requires(progression)
                .then(argument("player", EntityArgument.player()).then(argument("boss", StringArgumentType.word())
                    .then(argument("defeated", BoolArgumentType.bool()).executes(ctx -> {
                        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                        String boss = StringArgumentType.getString(ctx, "boss");
                        boolean value = BoolArgumentType.getBool(ctx, "defeated");
                        return run(ctx, () -> {
                            ProgressStore.setDefeated(name(p), p.getStringUUID(), boss, value);
                            return name(p) + ": " + boss + " defeated = " + value;
                        });
                    })))))
            .then(literal("bossreward").requires(AuthorizationChecks.requires(PermissionNodes.PLAYERSYNC_REWARD_GRANT))
                .then(argument("player", EntityArgument.player()).then(argument("boss", StringArgumentType.word())
                    .then(argument("slots", IntegerArgumentType.integer(0, 10)).executes(ctx -> {
                        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                        String boss = StringArgumentType.getString(ctx, "boss");
                        int slots = IntegerArgumentType.getInteger(ctx, "slots");
                        return run(ctx, () -> {
                            if (!ProgressStore.grantBossReward(name(p), p.getStringUUID(), boss, slots)) {
                                return name(p) + " already has the " + boss + " reward";
                            }
                            PlayerSync.announceBossReward(ctx.getSource().getServer(), p.getUUID(), boss);
                            return name(p) + ": " + boss + " reward given (+" + slots + " soulbound)";
                        });
                    })))))
            .then(literal("soulbound")
                .then(argument("player", EntityArgument.player())
                    .requires(AuthorizationChecks.requires(PermissionNodes.PLAYERSYNC_ITEMS_INSPECT))
                    .executes(ctx -> {
                        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                        return run(ctx, () -> name(p) + " may bind " + ProgressStore.soulboundSlots(name(p), p.getStringUUID()) + " item(s)");
                    })
                    .then(literal("add").requires(AuthorizationChecks.requires(PermissionNodes.PLAYERSYNC_ITEMS_MODIFY))
                        .then(argument("delta", IntegerArgumentType.integer(-100, 100)).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            int delta = IntegerArgumentType.getInteger(ctx, "delta");
                            return run(ctx, () -> {
                                ProgressStore.addSoulboundSlots(name(p), p.getStringUUID(), delta);
                                return name(p) + ": soulbound slots " + (delta >= 0 ? "+" : "") + delta;
                            });
                        })))))
            .then(literal("element").requires(progression)
                .then(argument("player", EntityArgument.player()).then(argument("element", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    String element = StringArgumentType.getString(ctx, "element");
                    int bit = PlayerSync.ElementProgress.bit(element);
                    if (bit == 0) {
                        ctx.getSource().sendFailure(Component.literal("Unknown element: air, water, earth or fire"));
                        return 0;
                    }
                    return run(ctx, () -> name(p) + (ProgressStore.unlockElement(name(p), p.getStringUUID(), bit)
                        ? ": " + element + " unlocked" : " already had " + element));
                }))))
            .then(literal("skillpoints").requires(progression)
                .then(argument("player", EntityArgument.player()).then(argument("delta", IntegerArgumentType.integer(-1000, 1000)).executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    int delta = IntegerArgumentType.getInteger(ctx, "delta");
                    return run(ctx, () -> {
                        ProgressStore.addSkillPoints(name(p), p.getStringUUID(), delta);
                        return name(p) + ": skill points " + (delta >= 0 ? "+" : "") + delta;
                    });
                }))))
            .then(literal("branch").requires(progression)
                .then(argument("player", EntityArgument.player()).then(argument("from", StringArgumentType.word())
                    .then(argument("to", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                        String from = StringArgumentType.getString(ctx, "from");
                        String to = StringArgumentType.getString(ctx, "to");
                        return run(ctx, () -> {
                            ProgressStore.switchBranch(name(p), p.getStringUUID(), from, to);
                            return name(p) + " moved from " + from + " to " + to;
                        });
                    })))))
            .then(literal("stage1").requires(progression)
                .then(argument("player", EntityArgument.player()).then(argument("cleared", BoolArgumentType.bool()).executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    boolean cleared = BoolArgumentType.getBool(ctx, "cleared");
                    return run(ctx, () -> {
                        ProgressStore.setStage1Cleared(name(p), p.getStringUUID(), cleared);
                        return name(p) + ": stage 1 cleared = " + cleared;
                    });
                }))))
            .then(literal("removeitems").requires(AuthorizationChecks.requires(PermissionNodes.PLAYERSYNC_ITEMS_MODIFY))
                .then(argument("player", EntityArgument.player()).then(argument("key", StringArgumentType.word())
                    .then(argument("value", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                        String key = StringArgumentType.getString(ctx, "key");
                        String value = StringArgumentType.getString(ctx, "value");
                        return run(ctx, () -> "Removed " + StoredItems.removeStored(p.getStringUUID(), key, value)
                            + " stored item(s) of " + name(p) + " with " + key + "=" + value);
                    }))))));
    }

    private static String name(ServerPlayer player) {
        return player.getName().getString();
    }

    /** Runs {@code job} on the worker and reports the result (or the error) to whoever ran the command. */
    private static int run(CommandContext<CommandSourceStack> ctx, Job job) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WORKER.execute(() -> {
            Component answer;
            try {
                answer = Component.literal(job.run());
            } catch (IllegalArgumentException wrongInput) {
                answer = Component.literal(wrongInput.getMessage()).withStyle(ChatFormatting.RED);
            } catch (Exception failure) {
                PlayerSyncMod.LOGGER.error("/playersync failed", failure);
                answer = Component.literal("Failed: " + failure.getMessage()).withStyle(ChatFormatting.RED);
            }
            Component message = answer;
            source.getServer().execute(() -> source.sendSystemMessage(message));
        });
        return 1;
    }
}
