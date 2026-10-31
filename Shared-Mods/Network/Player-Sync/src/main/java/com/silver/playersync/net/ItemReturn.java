package com.silver.playersync.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The proxy's "take these items back" exchange on channel {@code playersync:item_return}: the proxy asks the
 * player's server to remove every item whose custom data matches one of the given key/value pairs (carried and
 * stored), and the server answers with what it removed and what is left.
 */
public final class ItemReturn {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("playersync", "item_return");
    private static final int MAX_PAIRS = 32;

    private ItemReturn() {
    }

    public record Pair(String key, String value) {
    }

    public record Request(UUID id, List<Pair> pairs) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.ofMember(Request::write, Request::read);

        static Request read(RegistryFriendlyByteBuf buf) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            int count = buf.readInt();
            if (count < 0 || count > MAX_PAIRS) {
                throw new IllegalArgumentException("too many pairs: " + count);
            }
            List<Pair> pairs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                pairs.add(new Pair(readText(buf, 128), readText(buf, 256)));
            }
            return new Request(id, pairs);
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
            int count = Math.min(pairs.size(), MAX_PAIRS);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                writeText(buf, pairs.get(i).key(), 128);
                writeText(buf, pairs.get(i).value(), 256);
            }
        }

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record Response(UUID id, boolean success, int removedCarried, int removedStored, int leftCarried, int leftStored,
            String error) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.ofMember(Response::write, Response::read);

        static Response read(RegistryFriendlyByteBuf buf) {
            return new Response(new UUID(buf.readLong(), buf.readLong()), buf.readBoolean(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), readText(buf, 8192));
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
            buf.writeBoolean(success);
            buf.writeInt(removedCarried);
            buf.writeInt(removedStored);
            buf.writeInt(leftCarried);
            buf.writeInt(leftStored);
            writeText(buf, error, 8192);
        }

        @Override
        public Type<Response> type() {
            return TYPE;
        }
    }

    /** Text as a length (int) and UTF-8 bytes, the layout the proxy plugin reads and writes. */
    private static String readText(RegistryFriendlyByteBuf buf, int maxBytes) {
        int length = buf.readInt();
        if (length < 0 || length > maxBytes) {
            throw new IllegalArgumentException("text length out of range: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeText(RegistryFriendlyByteBuf buf, String text, int maxBytes) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("text too long: " + bytes.length);
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
}
