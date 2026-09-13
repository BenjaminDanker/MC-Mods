package com.silver.portalprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PortalRequestPayload(byte[] payload) implements CustomPacketPayload {
    public static final Type<PortalRequestPayload> PACKET_ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("wakeuplobby", "portal_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalRequestPayload> codec =
            StreamCodec.ofMember(PortalRequestPayload::write, PortalRequestPayload::read);

    public static PortalRequestPayload read(RegistryFriendlyByteBuf buf) {
        int remaining = buf.readableBytes();
        byte[] bytes = new byte[Math.max(0, remaining)];
        buf.readBytes(bytes);
        return new PortalRequestPayload(bytes);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        if (payload != null && payload.length > 0) {
            buf.writeBytes(payload);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
