package com.silver.enderfight.portal;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.reset.EndResetManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles proxy requests to return a player to the overworld after a failed End portal handoff.
 */
public final class ReturnOverworldHandler {
    public static final CustomPacketPayload.Type<ReturnOverworldPayload> PACKET_ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("wakeuplobby", "return_overworld"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReturnOverworldPayload> codec =
        StreamCodec.ofMember(ReturnOverworldPayload::write, ReturnOverworldPayload::read);

    private ReturnOverworldHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PACKET_ID,
            (payload, context) -> context.server().execute(() -> handleRequest(context.player())));
    }

    private static void handleRequest(ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (!PortalInterceptor.isManagedEndDimension(player.createCommandSourceStack().getLevel().dimension())) {
            return;
        }

        EndResetManager manager = EnderFightMod.getEndResetManager();
        if (manager == null) {
            return;
        }

        Component message = Component.literal("You have been returned to the Overworld.");
        manager.teleportPlayerToOverworld(player, message, "WakeUpLobby /return");
    }

    public record ReturnOverworldPayload() implements CustomPacketPayload {
        public static ReturnOverworldPayload read(RegistryFriendlyByteBuf buf) {
            return new ReturnOverworldPayload();
        }

        public void write(RegistryFriendlyByteBuf buf) {
            // No payload data.
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }
}
