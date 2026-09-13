package com.silver.aipets.fabric.conversation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Pet adaptation of Villager-Interface's private chat transport: the normal Minecraft chat text
 * box supplies input, while a same-tick broadcast hook suppresses every captured message.
 */
public final class PrivateChatPetTextInputUi implements PetTextInputUi {
    private static final String EXIT = "!exit";

    private final Clock clock;
    private final Map<UUID, ActiveInput> active = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastHandledTick = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> suppressNextBroadcast = new ConcurrentHashMap<>();

    public PrivateChatPetTextInputUi(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static PrivateChatPetTextInputUi systemClock() {
        return new PrivateChatPetTextInputUi(Clock.systemUTC());
    }

    @Override
    public void open(
            ServerPlayer owner,
            PetConversationSession session,
            String petName,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(petName, "petName");
        ActiveInput replacement = new ActiveInput(
                session, petName, Objects.requireNonNull(onSubmit, "onSubmit"),
                Objects.requireNonNull(onCancel, "onCancel"));
        ActiveInput current = active.get(owner.getUUID());
        if (current != null && current.session().sessionId().equals(session.sessionId())) {
            return;
        }
        ActiveInput previous = active.put(owner.getUUID(), replacement);
        if (previous != null) previous.onCancel().run();
        owner.sendSystemMessage(Component.literal(
                "Chatting privately with " + petName + ". Type messages normally; use !exit when you're done.")
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** Called at the head of the vanilla chat packet handler, following Villager-Interface. */
    public boolean handleChatMessage(ServerPlayer player, String rawMessage) {
        Objects.requireNonNull(player, "player");
        ActiveInput current = active.get(player.getUUID());
        if (current == null) return false;
        lastHandledTick.put(player.getUUID(), player.level().getServer().getTickCount());
        suppressNextBroadcast.put(player.getUUID(), Boolean.TRUE);

        String message = rawMessage == null ? "" : rawMessage.strip();
        if (message.isEmpty()) return true;
        if (EXIT.equalsIgnoreCase(message)) {
            if (active.remove(player.getUUID(), current)) {
                current.onCancel().run();
                player.sendSystemMessage(Component.literal(
                        "Your conversation with " + current.petName() + " has ended."), false);
            }
            return true;
        }

        player.sendSystemMessage(Component.literal("<" + player.getName().getString() + "> " + message)
                .withStyle(ChatFormatting.GRAY), false);
        player.sendSystemMessage(Component.literal("Your pet is thinking...")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        current.onSubmit().accept(message);
        return true;
    }

    /** Called from the broadcast hook after handleChatMessage removed a submitted session. */
    public boolean shouldSuppressBroadcast(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (active.containsKey(player.getUUID())) return true;
        if (suppressNextBroadcast.remove(player.getUUID()) != null) return true;
        Integer handledTick = lastHandledTick.get(player.getUUID());
        return handledTick != null
                && player.level().getServer().getTickCount() - handledTick <= 5;
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        lastHandledTick.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            boolean expired = player == null
                    || entry.getValue() + 5 < player.level().getServer().getTickCount();
            if (expired) suppressNextBroadcast.remove(entry.getKey());
            return expired;
        });
    }

    public boolean isActive(UUID ownerUuid) {
        return active.containsKey(Objects.requireNonNull(ownerUuid, "ownerUuid"));
    }

    public void cancelOwner(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        ActiveInput removed = active.remove(ownerUuid);
        if (removed != null) removed.onCancel().run();
        lastHandledTick.remove(ownerUuid);
        suppressNextBroadcast.remove(ownerUuid);
    }

    @Override
    public void close(ServerPlayer owner, String message) {
        Objects.requireNonNull(owner, "owner");
        ActiveInput removed = active.remove(owner.getUUID());
        if (removed != null) {
            removed.onCancel().run();
            owner.sendSystemMessage(Component.literal(message), false);
        }
        lastHandledTick.remove(owner.getUUID());
        suppressNextBroadcast.remove(owner.getUUID());
    }

    public void endOwner(ServerPlayer owner, String message) {
        close(owner, message);
    }

    public void cancelPet(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        active.forEach((ownerUuid, current) -> {
            if (current.session().petId().equals(petId)
                    && active.remove(ownerUuid, current)) {
                current.onCancel().run();
            }
        });
    }

    public void clear() {
        active.values().forEach(current -> current.onCancel().run());
        active.clear();
        lastHandledTick.clear();
        suppressNextBroadcast.clear();
    }

    private record ActiveInput(
            PetConversationSession session,
            String petName,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        private ActiveInput {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(petName, "petName");
            Objects.requireNonNull(onSubmit, "onSubmit");
            Objects.requireNonNull(onCancel, "onCancel");
        }
    }
}
