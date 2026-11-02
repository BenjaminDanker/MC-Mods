package com.silver.authorization.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Authenticated backend command inventory sent to Velocity for /auth reporting. */
public record CommandCatalogPayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<CommandCatalogPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath("silverauth", "command_catalog_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandCatalogPayload> CODEC =
            StreamCodec.ofMember(CommandCatalogPayload::write, CommandCatalogPayload::read);

    public CommandCatalogPayload {
        if (bytes == null || bytes.length == 0 || bytes.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid command-catalog payload size");
        }
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }

    public static CommandCatalogPayload read(RegistryFriendlyByteBuf buf) {
        int length = buf.readableBytes();
        if (length <= 0 || length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid command-catalog frame size");
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return new CommandCatalogPayload(data);
    }

    public void write(RegistryFriendlyByteBuf buf) { buf.writeBytes(bytes); }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
