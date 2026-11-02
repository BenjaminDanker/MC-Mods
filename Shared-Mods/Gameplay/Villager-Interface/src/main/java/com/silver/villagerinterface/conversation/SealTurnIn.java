package com.silver.villagerinterface.conversation;

import net.minecraft.server.level.ServerPlayer;

/** A quartermaster NPC takes a story's Seal: Magitech Core does the turn-in (called by name; absent elsewhere). */
public final class SealTurnIn {
    private SealTurnIn() {
    }

    /** True when the held item was a Seal and Magitech Core handled it (turned in, or told the player why not). */
    public static boolean tryTurnIn(ServerPlayer player) {
        try {
            Class<?> api = Class.forName("com.silver.magitech.MagitechApi");
            return (boolean) api.getMethod("turnInHeldSeal", ServerPlayer.class).invoke(null, player);
        } catch (ReflectiveOperationException notHere) {
            return false;
        }
    }
}
