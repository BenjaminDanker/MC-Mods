package com.silver.wakeuplobby.portal;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Raw custom payload sent from the backend server to the proxy.
 * The payload bytes are interpreted by WakeUpLobby.
 */
public record PortalRequestPayload(byte[] payload) implements CustomPayload {
    public static final Id<PortalRequestPayload> PACKET_ID =
        new CustomPayload.Id<>(Identifier.of("wakeuplobby", "portal_request"));

    public static final PacketCodec<RegistryByteBuf, PortalRequestPayload> codec =
        PacketCodec.of(PortalRequestPayload::write, PortalRequestPayload::read);

    public static PortalRequestPayload read(RegistryByteBuf buf) {
        int remaining = buf.readableBytes();
        byte[] bytes = new byte[Math.max(0, remaining)];
        buf.readBytes(bytes);
        return new PortalRequestPayload(bytes);
    }

    public void write(RegistryByteBuf buf) {
        if (payload != null && payload.length > 0) {
            buf.writeBytes(payload);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
