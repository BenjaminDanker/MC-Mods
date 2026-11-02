package com.silver.authorization.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Dedicated payload ID; Velocity handles it internally and never forwards it to clients. */
public record AuthorizationPayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<AuthorizationPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath("silverauth", "sync_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorizationPayload> CODEC =
            StreamCodec.ofMember(AuthorizationPayload::write, AuthorizationPayload::read);

    public AuthorizationPayload {
        if (bytes == null || bytes.length == 0 || bytes.length > 256 * 1024) {
            throw new IllegalArgumentException("Invalid authorization payload size");
        }
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }

    public static AuthorizationPayload read(RegistryFriendlyByteBuf buf) {
        int length = buf.readableBytes();
        if (length <= 0 || length > 256 * 1024) throw new IllegalArgumentException("Invalid authorization frame size");
        byte[] data = new byte[length];
        buf.readBytes(data);
        return new AuthorizationPayload(data);
    }

    public void write(RegistryFriendlyByteBuf buf) { buf.writeBytes(bytes); }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
