package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional, dependency-free guard against owning Villager-Interface and pet chat simultaneously. */
final class VillagerPrivateChatCompat {
    private static final String MOD_ID = "villagerinterface";
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    private VillagerPrivateChatCompat() {
    }

    static boolean isConversationActive(ServerPlayerEntity player) {
        Objects.requireNonNull(player, "player");
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
        try {
            Class<?> entrypoint = Class.forName(
                    "com.silver.villagerinterface.VillagerInterfaceMod", false,
                    VillagerPrivateChatCompat.class.getClassLoader());
            Method getManager = entrypoint.getMethod("getConversationManager");
            Object manager = getManager.invoke(null);
            if (manager == null) return false;
            Method isInConversation = manager.getClass().getMethod(
                    "isInConversation", ServerPlayerEntity.class);
            return Boolean.TRUE.equals(isInConversation.invoke(manager, player));
        } catch (ReflectiveOperationException | LinkageError failure) {
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                PetCompanionMod.LOGGER.warn(StructuredPetEvent
                        .operation("villager_chat_compatibility")
                        .failure(failure).outcome("fail_closed").toJson());
            }
            // Fail closed so two private-chat consumers cannot trigger two model calls.
            return true;
        }
    }
}
