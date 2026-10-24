package com.silver.gaterites;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** What both gates do to someone who is not the traveller they were opened for. */
final class Gate {
    /** Ticks between two refusals for the same person, so standing in a gate does not flood the chat. */
    static final int REFUSAL_SPACING_TICKS = 60;

    private Gate() {
    }

    /** Pushes a stranger back along {@code away} (horizontal) and tells them, now and then, that the gate does not know them. */
    static void refuse(ServerPlayer stranger, Vec3 away, boolean tell) {
        Vec3 flat = new Vec3(away.x, 0, away.z);
        Vec3 push = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        stranger.push(push.x * 0.9, 0.2, push.z * 0.9);
        stranger.hurtMarked = true;
        if (tell) {
            stranger.sendSystemMessage(Component.literal("The gate does not know you.").withStyle(ChatFormatting.GRAY), true);
        }
    }
}
