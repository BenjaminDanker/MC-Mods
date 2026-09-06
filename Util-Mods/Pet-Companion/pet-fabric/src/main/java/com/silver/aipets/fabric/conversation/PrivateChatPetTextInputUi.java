package com.silver.aipets.fabric.conversation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public PrivateChatPetTextInputUi(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static PrivateChatPetTextInputUi systemClock() {
        return new PrivateChatPetTextInputUi(Clock.systemUTC());
    }

    @Override
    public void open(
            ServerPlayerEntity owner,
            PetConversationSession session,
            String petName,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(petName, "petName");
        ActiveInput replacement = new ActiveInput(
                session, Objects.requireNonNull(onSubmit, "onSubmit"),
                Objects.requireNonNull(onCancel, "onCancel"));
        ActiveInput previous = active.put(owner.getUuid(), replacement);
        if (previous != null) previous.onCancel().run();
        owner.sendMessage(Text.literal(
                "Chatting privately with " + petName + ". Type a message, or !exit to cancel.")
                .formatted(Formatting.GRAY), false);
    }

    /** Called at the head of the vanilla chat packet handler, following Villager-Interface. */
    public boolean handleChatMessage(ServerPlayerEntity player, String rawMessage) {
        Objects.requireNonNull(player, "player");
        ActiveInput current = active.get(player.getUuid());
        if (current == null) return false;
        lastHandledTick.put(player.getUuid(), player.getEntityWorld().getServer().getTicks());

        String message = rawMessage == null ? "" : rawMessage.strip();
        if (message.isEmpty()) return true;
        if (EXIT.equalsIgnoreCase(message)) {
            if (active.remove(player.getUuid(), current)) {
                current.onCancel().run();
                player.sendMessage(Text.literal("Pet conversation cancelled."), false);
            }
            return true;
        }
        if (!active.remove(player.getUuid(), current)) return true;

        player.sendMessage(Text.literal("<" + player.getName().getString() + "> " + message)
                .formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("Your pet is thinking...")
                .formatted(Formatting.DARK_GRAY), false);
        current.onSubmit().accept(message);
        return true;
    }

    /** Called from the broadcast hook after handleChatMessage removed a submitted session. */
    public boolean shouldSuppressBroadcast(ServerPlayerEntity player) {
        Objects.requireNonNull(player, "player");
        if (active.containsKey(player.getUuid())) return true;
        Integer handledTick = lastHandledTick.get(player.getUuid());
        return handledTick != null
                && handledTick == player.getEntityWorld().getServer().getTicks();
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        active.forEach((ownerUuid, current) -> {
            if (!current.session().isExpired(clock.instant())) return;
            if (!active.remove(ownerUuid, current)) return;
            current.onCancel().run();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerUuid);
            if (player != null) {
                player.sendMessage(Text.literal(
                        "Pet conversation expired. Right-click your pet again."), false);
            }
        });
        lastHandledTick.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            return player == null
                    || entry.getValue() + 1 < player.getEntityWorld().getServer().getTicks();
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
    }

    private record ActiveInput(
            PetConversationSession session,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        private ActiveInput {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(onSubmit, "onSubmit");
            Objects.requireNonNull(onCancel, "onCancel");
        }
    }
}
