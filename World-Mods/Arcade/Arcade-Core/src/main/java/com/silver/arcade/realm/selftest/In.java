package com.silver.arcade.realm.selftest;

import java.time.Instant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a bot "does", sent into the server the way a client's packets arrive: a use-on-block packet, a start-destroy
 * packet, a chat message. All of these call on the server thread.
 */
final class In {
    private In() {
    }

    /** Right-click a block (the same handler a client's packet reaches). */
    static void use(ServerPlayer p, BlockPos pos) {
        com.silver.arcade.selftest.Bots.ready(p);
        com.silver.arcade.selftest.Bots.ack(p);
        p.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false), 0));
    }

    /** Left-click (start digging) a block. */
    static void hit(ServerPlayer p, BlockPos pos) {
        com.silver.arcade.selftest.Bots.ready(p);
        com.silver.arcade.selftest.Bots.ack(p);
        p.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.NORTH, 0));
    }

    /**
     * A chat line. Signed chat needs a real client's key, so the line enters at the server's broadcast step, which is where
     * the chat event the product listens to is fired from.
     */
    static void say(ServerPlayer p, String text) {
        PlayerChatMessage message = PlayerChatMessage.unsigned(p.getUUID(), text);
        p.level().getServer().getPlayerList().broadcastChatMessage(message, p, ChatType.bind(ChatType.CHAT, p));
    }

    static String stamp() {
        return Instant.now().toString();
    }
}
