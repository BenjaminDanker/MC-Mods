package com.silver.aipets.fabric.conversation;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

/** Private Minecraft text-entry boundary; opening it must never submit a model request. */
@FunctionalInterface
public interface PetTextInputUi {
    void open(
            ServerPlayerEntity owner,
            PetConversationSession session,
            String petName,
            Consumer<String> onSubmit,
            Runnable onCancel);

    /** Ends any open input owned by this player. Implementations may notify the player. */
    default void close(ServerPlayerEntity owner, String message) {
    }
}
