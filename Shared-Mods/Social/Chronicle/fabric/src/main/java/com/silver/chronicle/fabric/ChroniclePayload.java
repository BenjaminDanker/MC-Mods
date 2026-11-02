package com.silver.chronicle.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChroniclePayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<ChroniclePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("chronicle", "events_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChroniclePayload> CODEC =
            StreamCodec.ofMember(ChroniclePayload::write, ChroniclePayload::read);

    public ChroniclePayload {
        if (bytes == null || bytes.length == 0 || bytes.length > 32_768) throw new IllegalArgumentException("Invalid Chronicle payload size");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    public static ChroniclePayload read(RegistryFriendlyByteBuf buf) {
        int length = buf.readableBytes();
        if (length <= 0 || length > 32_768) throw new IllegalArgumentException("Invalid Chronicle frame size");
        byte[] data = new byte[length]; buf.readBytes(data); return new ChroniclePayload(data);
    }
    public void write(RegistryFriendlyByteBuf buf) { buf.writeBytes(bytes); }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
