package com.silver.arcade.realm;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat in the arcade stays where it is said: inside a climb only that climb's players hear it, in the hall only the
 * players in the hall do. So a busy arcade never buries the Gamemaster's lines for a group, and a group still talks
 * freely. Lines look like normal chat (Pretty Chat's format when it is installed).
 */
final class ScopedChat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Arcade");
    private static Method render;
    private static boolean looked;

    private ScopedChat() {
    }

    /** Sends {@code message} to everyone in the same place as the sender: same climb (scope), or the hall (null). */
    static void send(MinecraftServer server, ServerPlayer sender, PlayerChatMessage message, ChatType.Bound params,
            Function<UUID, Object> scopeOf) {
        Object scope = scopeOf.apply(sender.getUUID());
        Component pretty = pretty(sender, message.decoratedContent());
        OutgoingChatMessage outgoing = OutgoingChatMessage.create(message);
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (scopeOf.apply(recipient.getUUID()) != scope) {
                continue;
            }
            if (pretty != null) {
                recipient.sendSystemMessage(pretty, false);
            } else {
                recipient.sendChatMessage(outgoing, false, params);
            }
        }
        LOGGER.info("[{} chat] <{}> {}", scope == null ? "hall" : "climb", sender.getGameProfile().name(), message.signedContent());
    }

    /** Pretty Chat's local format, if that mod is installed (looked up once, by reflection: no hard dependency). */
    private static Component pretty(ServerPlayer sender, Component body) {
        if (!looked) {
            looked = true;
            try {
                render = Class.forName("com.silver.prettychat.fabric.LocalChatFormatter")
                    .getMethod("render", UUID.class, String.class, Component.class);
            } catch (ReflectiveOperationException e) {
                render = null;
            }
        }
        if (render == null) {
            return null;
        }
        try {
            return (Component) render.invoke(null, sender.getUUID(), sender.getGameProfile().name(), body);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
