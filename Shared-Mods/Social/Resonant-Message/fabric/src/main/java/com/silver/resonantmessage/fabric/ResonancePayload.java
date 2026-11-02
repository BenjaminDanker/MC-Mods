package com.silver.resonantmessage.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ResonancePayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<ResonancePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("resonant", "message_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonancePayload> CODEC =
            StreamCodec.ofMember(ResonancePayload::write, ResonancePayload::read);

    public ResonancePayload {
        if (bytes == null || bytes.length == 0 || bytes.length > 8192) {
            throw new IllegalArgumentException("Invalid Resonance payload size");
        }
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }

    public static ResonancePayload read(RegistryFriendlyByteBuf buf) {
        int length = buf.readableBytes();
        if (length <= 0 || length > 8192) throw new IllegalArgumentException("Invalid Resonance frame size");
        byte[] data = new byte[length];
        buf.readBytes(data);
        return new ResonancePayload(data);
    }

    public void write(RegistryFriendlyByteBuf buf) { buf.writeBytes(bytes); }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}