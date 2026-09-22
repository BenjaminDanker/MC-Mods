package com.silver.resonantmessage.fabric;

import com.silver.resonantmessage.common.ResonanceProtocol;
import com.silver.resonantmessage.common.ResonanceProtocol.Acknowledgement;
import com.silver.resonantmessage.common.ResonanceProtocol.Request;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/** Holds the one-message state locally and consumes only signed proxy acceptance. */
final class ResonantMessageRuntime {
    private static final String PRIMED_TEXT =
            "Your amethyst resonates. Your next message will be heard across worlds.";
    private static final String CANCELLED_TEXT = "Your amethyst resonance was cancelled.";

    private final BackendConfig config;
    private final Logger logger;
    private final UUID backendEpoch = UUID.randomUUID();
    private final Map<UUID, Primed> primed = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    ResonantMessageRuntime(BackendConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> onGenericItemUse(player, world.isClientSide(), hand));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::onChat);
        ServerPlayNetworking.registerGlobalReceiver(ResonancePayload.ID, (payload, context) -> {
            byte[] bytes = payload.bytes();
            context.server().execute(() -> acceptAcknowledgement(context.player(), bytes));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID playerId : primed.keySet().toArray(UUID[]::new)) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Primed state = primed.get(playerId);
                if (player == null) cancel(playerId, null);
                else if (state != null && !stillHolding(player, state)) cancel(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (UUID playerId : primed.keySet().toArray(UUID[]::new)) {
                cancel(playerId, server.getPlayerList().getPlayer(playerId));
            }
            pending.clear();
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) cancel(player);
        });
    }

    void onSelectedSlotChange(ServerPlayer player, int newSlot) {
        Primed state = primed.get(player.getUUID());
        if (state != null && state.selectedSlot() != newSlot) cancel(player);
    }

    void onPossibleInventoryChange(ServerPlayer player) {
        if (player == null) return;
        Primed state = primed.get(player.getUUID());
        if (state != null && !stillHolding(player, state)) cancel(player);
    }
    private InteractionResult onGenericItemUse(Player player, boolean clientSide, InteractionHand hand) {
        if (clientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ItemStack held = serverPlayer.getItemInHand(hand);
        if (held.isEmpty() || !held.is(Items.AMETHYST_SHARD)) {
            logger.debug("[ResonantMessage] Use-item callback ignored player={} hand={} item={} reason=not_amethyst_shard",
                    serverPlayer.getName().getString(), hand, held.getItem());
            return InteractionResult.PASS;
        }

        String playerName = serverPlayer.getName().getString();
        logger.info("[ResonantMessage] Use-item callback fired player={} hand={} item={}",
                playerName, hand, held.getItem());
        UUID playerId = serverPlayer.getUUID();
        Primed previous = primed.get(playerId);
        if (previous != null && !stillHolding(serverPlayer, previous)) {
            cancel(serverPlayer);
            previous = null;
        }
        if (previous != null) {
            logger.info("[ResonantMessage] Priming ignored player={} hand={} item={} result=already_primed",
                    playerName, hand, held.getItem());
            return InteractionResult.PASS;
        }

        if (config.key().isEmpty()) {
            logger.warn("[ResonantMessage] Priming ignored player={} hand={} item={} result=authentication_unavailable",
                    playerName, hand, held.getItem());
            return InteractionResult.PASS;
        }
        primed.put(playerId, new Primed(hand, serverPlayer.getInventory().getSelectedSlot(), held));
        serverPlayer.sendSystemMessage(Component.literal(PRIMED_TEXT), false);
        logger.info("[ResonantMessage] Priming accepted player={} hand={} item={} result=primed",
                playerName, hand, held.getItem());
        return InteractionResult.PASS;
    }

    private boolean onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound parameters) {
        UUID playerId = sender.getUUID();
        Primed state = primed.get(playerId);
        if (state == null) return true;
        if (!stillHolding(sender, state)) {
            cancel(sender);
            return true;
        }
        if (pending.containsKey(playerId)) return false;

        String text = message.signedContent();
        if (!ResonanceProtocol.validMessage(text)) return true;
        Optional<byte[]> key = config.key();
        if (key.isEmpty()) {
            cancel(sender);
            return true;
        }

        UUID nonce = UUID.randomUUID();
        Request request = ResonanceProtocol.sign(new Request(ResonanceProtocol.VERSION,
                config.backendId(), playerId, text, backendEpoch, nonce, System.currentTimeMillis(), ""),
                key.orElseThrow());
        Pending inFlight = new Pending(state, nonce);
        pending.put(playerId, inFlight);
        try {
            ServerPlayNetworking.send(sender, new ResonancePayload(ResonanceProtocol.encode(request)));
        } catch (RuntimeException failure) {
            pending.remove(playerId, inFlight);
            logger.warn("[ResonantMessage] Could not submit a Resonant Message request: {}", failure.toString());
        }
        return false;
    }

    private void acceptAcknowledgement(ServerPlayer player, byte[] frame) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        Pending inFlight = pending.get(playerId);
        if (inFlight == null) return;
        try {
            Acknowledgement acknowledgement = ResonanceProtocol.decodeAcknowledgement(frame);
            Optional<byte[]> key = config.key();
            if (key.isEmpty()
                    || acknowledgement.protocolVersion() != ResonanceProtocol.VERSION
                    || !acknowledgement.backendId().equals(config.backendId())
                    || !acknowledgement.playerId().equals(playerId)
                    || !acknowledgement.backendEpoch().equals(backendEpoch)
                    || !acknowledgement.nonce().equals(inFlight.nonce())
                    || !ResonanceProtocol.verify(acknowledgement, key.orElseThrow())) return;
            pending.remove(playerId, inFlight);
            if (!acknowledgement.accepted()) return;

            Primed current = primed.get(playerId);
            if (current != inFlight.primed() || !stillHolding(player, current)) {
                cancel(player);
                return;
            }
            ItemStack shardStack = player.getItemInHand(current.hand());
            if (shardStack.getCount() == 1) {
                player.setItemInHand(current.hand(), ItemStack.EMPTY);
            } else {
                shardStack.shrink(1);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            clear(playerId);
        } catch (RuntimeException malformed) {
            logger.debug("[ResonantMessage] Rejected malformed proxy acknowledgement: {}", malformed.toString());
        }
    }

    private static boolean stillHolding(ServerPlayer player, Primed state) {
        return player.getInventory().getSelectedSlot() == state.selectedSlot()
                && player.getItemInHand(state.hand()) == state.stack()
                && !state.stack().isEmpty()
                && state.stack().is(Items.AMETHYST_SHARD);
    }

    private void cancel(ServerPlayer player) {
        cancel(player.getUUID(), player);
    }

    private void cancel(UUID playerId, ServerPlayer player) {
        Primed removed = primed.remove(playerId);
        pending.remove(playerId);
        if (removed != null && player != null) {
            player.sendSystemMessage(Component.literal(CANCELLED_TEXT), false);
            logger.info("[ResonantMessage] Priming cancelled player={} reason=state_cancelled",
                    player.getName().getString());
        }
    }

    private void clear(UUID playerId) {
        primed.remove(playerId);
        pending.remove(playerId);
    }

    private record Primed(InteractionHand hand, int selectedSlot, ItemStack stack) {}
    private record Pending(Primed primed, UUID nonce) {}
}
