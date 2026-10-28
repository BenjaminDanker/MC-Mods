package com.silver.arcade.realm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** The Gamemaster's voice: chat lines, titles, and "explained once" memory (player tags on this server). */
public final class Gamemaster {
    private static final String SEEN_PREFIX = "arcade_seen_";

    private Gamemaster() {
    }

    public static void say(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("Gamemaster: ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(style -> style.withBold(false))));
    }

    public static void title(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.GOLD)));
    }

    public static void bar(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }

    /** Explains a room the first time this player meets it; afterwards only names it. */
    public static void introduce(ServerPlayer player, Room room, int tier) {
        title(player, room.title(), "Tier " + tier);
        if (player.addTag(SEEN_PREFIX + room.id())) {
            say(player, room.explain());
        } else {
            bar(player, "Type !explain to hear the rules again.");
        }
    }

    public static void explainAgain(ServerPlayer player, Room room) {
        say(player, room.explain());
    }
}
