package com.silver.portals;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.HexColorArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /portals list}, {@code register <name> <frame> <item> <color> <server> [toPortal]}, {@code unregister
 * <name>}, {@code send <player> <server>}. Registrations take effect at the next start.
 */
final class PortalsCommands {
    private PortalsCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registries) {
        var manage = AuthorizationChecks.requires(PermissionNodes.PORTAL_REGISTRY_MANAGE);
        dispatcher.register(literal("portals")
            .then(literal("list").requires(AuthorizationChecks.requires(PermissionNodes.PORTAL_REGISTRY_VIEW)).executes(ctx -> {
                if (PortalsMod.portals().isEmpty()) {
                    reply(ctx.getSource(), "No portals on this server.");
                }
                PortalsMod.portals().forEach(portal -> reply(ctx.getSource(), portal.toString()));
                return PortalsMod.portals().size();
            }))
            .then(literal("register").requires(manage)
                .then(argument("name", StringArgumentType.word())
                    .then(argument("frame", BlockStateArgument.block(registries))
                        .then(argument("lightWith", ItemArgument.item(registries))
                            .then(argument("color", HexColorArgument.hexColor())
                                .then(argument("server", StringArgumentType.word())
                                    .executes(ctx -> register(ctx, null))
                                    .then(argument("toPortal", StringArgumentType.word())
                                        .executes(ctx -> register(ctx, StringArgumentType.getString(ctx, "toPortal"))))))))))
            .then(literal("unregister").requires(manage)
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    boolean removed = PortalsMod.unregister(name);
                    reply(ctx.getSource(), removed ? "Removed portal " + name + "; it stops working at the next start."
                        : "There is no portal " + name + ".");
                    return removed ? 1 : 0;
                })))
            // the home page's admin side types this into the console to move a player
            .then(literal("send").requires(manage)
                .then(argument("player", StringArgumentType.word()).then(argument("server", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    String server = StringArgumentType.getString(ctx, "server");
                    ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if (player == null) {
                        reply(ctx.getSource(), name + " is not on this server.");
                        return 0;
                    }
                    boolean sent = Travel.send(player, server, "", "admin send of " + name);
                    reply(ctx.getSource(), sent ? "Sending " + name + " to " + server + "." : "Could not send " + name + " (see the log).");
                    return sent ? 1 : 0;
                })))));
    }

    private static int register(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String toPortal) {
        PortalConfig.Portal portal = new PortalConfig.Portal();
        portal.name = StringArgumentType.getString(ctx, "name");
        portal.frame = BuiltInRegistries.BLOCK.getKey(BlockStateArgument.getBlock(ctx, "frame").getState().getBlock()).toString();
        portal.lightWith = BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(ctx, "lightWith").item().value()).toString();
        Integer color = HexColorArgument.getHexColor(ctx, "color");
        portal.color = color == null ? 0 : color & 0xFFFFFF; // plain RGB, like converted configs
        portal.toServer = StringArgumentType.getString(ctx, "server");
        portal.toPortal = toPortal;
        if (!PortalsMod.register(portal)) {
            reply(ctx.getSource(), "A portal called " + portal.name + " already exists; unregister it first.");
            return 0;
        }
        reply(ctx.getSource(), "Registered " + portal + "; it works from the next start.");
        return 1;
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text));
    }
}
