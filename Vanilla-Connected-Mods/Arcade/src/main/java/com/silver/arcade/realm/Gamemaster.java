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

    /**
     * A short signal in the middle of the screen, kept small: only the subtitle line is used (the big title stays empty),
     * shown briefly. The boss bar names the room and the action bar carries status, so nothing else needs the screen.
     */
    public static void title(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(3, 25, 8));
        Component line = Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (subtitle != null && !subtitle.isBlank()) {
            line = line.copy().append(Component.literal("  " + subtitle).withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(false)));
        }
        player.connection.send(new ClientboundSetSubtitleTextPacket(line));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }

    public static void bar(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }

    /** Explains a room the first time this player meets it; afterwards only names it. */
    /** Returns true when the rules were explained (the first time this player meets the room). */
    public static boolean introduce(ServerPlayer player, Room room, int tier) {
        // No title: the boss bar already names the room and the tier.
        if (player.addTag(SEEN_PREFIX + room.id())) {
            say(player, room.explain());
            player.sendSystemMessage(Component.literal("/explain repeats the rules  \u00b7  /giveup ends the room").withStyle(ChatFormatting.DARK_GRAY));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("/explain repeats the rules  \u00b7  /giveup ends the room").withStyle(ChatFormatting.DARK_GRAY));
        }
        return false;
    }

    public static void explainAgain(ServerPlayer player, Room room) {
        say(player, room.explain());
    }
}
