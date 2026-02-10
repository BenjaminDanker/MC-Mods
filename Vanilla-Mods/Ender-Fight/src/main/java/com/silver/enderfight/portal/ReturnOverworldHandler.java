package com.silver.enderfight.portal;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.reset.EndResetManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Handles proxy requests to return a player to the overworld after a failed End portal handoff.
 */
public final class ReturnOverworldHandler {
    public static final CustomPayload.Id<ReturnOverworldPayload> PACKET_ID =
        new CustomPayload.Id<>(Identifier.of("wakeuplobby", "return_overworld"));

    public static final PacketCodec<RegistryByteBuf, ReturnOverworldPayload> codec =
        PacketCodec.of(ReturnOverworldPayload::write, ReturnOverworldPayload::read);

    private ReturnOverworldHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PACKET_ID,
            (payload, context) -> context.server().execute(() -> handleRequest(context.player())));
    }

    private static void handleRequest(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        if (!PortalInterceptor.isManagedEndDimension(player.getCommandSource().getWorld().getRegistryKey())) {
            return;
        }

        EndResetManager manager = EnderFightMod.getEndResetManager();
        if (manager == null) {
            return;
        }

        Text message = Text.literal("You have been returned to the Overworld.");
        manager.teleportPlayerToOverworld(player, message, "WakeUpLobby /return");
    }

    public record ReturnOverworldPayload() implements CustomPayload {
        public static ReturnOverworldPayload read(RegistryByteBuf buf) {
            return new ReturnOverworldPayload();
        }

        public void write(RegistryByteBuf buf) {
            // No payload data.
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return PACKET_ID;
        }
    }
}
