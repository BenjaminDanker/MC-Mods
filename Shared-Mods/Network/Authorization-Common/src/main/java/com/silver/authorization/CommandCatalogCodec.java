package com.silver.authorization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Compact HMAC-SHA256 codec with command-catalog-specific domain separation. */
public final class CommandCatalogCodec {
    private static final int MAGIC = 0x53414331; // SAC1
    private static final int SIGNATURE_BYTES = 32;
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    private static final byte[] DOMAIN = "silverauth-command-catalog-v1\0".getBytes(StandardCharsets.US_ASCII);

    private CommandCatalogCodec() { }

    public static byte[] encodeSigned(CommandCatalog catalog, byte[] key) {
        validateKey(key);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(catalog.protocolVersion());
                writeString(out, catalog.serverId().value(), 64);
                writeUuid(out, catalog.backendEpoch());
                out.writeLong(catalog.generation());
                writeUuid(out, catalog.nonce());
                out.writeLong(catalog.issuedAt().toEpochMilli());
                out.writeInt(catalog.entries().size());
                for (CommandCatalogEntry entry : catalog.entries()) {
                    writeString(out, entry.path().canonical(), 512);
                    out.writeBoolean(entry.executable());
                    writeOptional(out, entry.source(), 96);
                    writeOptional(out, entry.modId(), 96);
                }
            }
            byte[] body = bytes.toByteArray();
            byte[] signature = mac(key, body);
            if (body.length + signature.length > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("Signed command catalog exceeds frame limit");
            }
            ByteArrayOutputStream frame = new ByteArrayOutputStream(body.length + signature.length);
            frame.write(body);
            frame.write(signature);
            return frame.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory command-catalog encoding failed", impossible);
        }
    }

    public static CommandCatalog decodeAndVerify(byte[] frame, byte[] key) {
        validateKey(key);
        if (frame == null || frame.length <= SIGNATURE_BYTES || frame.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("Invalid command-catalog frame size");
        }
        int bodyLength = frame.length - SIGNATURE_BYTES;
        byte[] body = java.util.Arrays.copyOf(frame, bodyLength);
        byte[] signature = java.util.Arrays.copyOfRange(frame, bodyLength, frame.length);
        if (!MessageDigest.isEqual(signature, mac(key, body))) {
            throw new IllegalArgumentException("Invalid command-catalog signature");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid command-catalog magic");
            int version = in.readInt();
            if (version != CommandCatalog.CURRENT_PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Incompatible command-catalog protocol");
            }
            ServerId server = ServerId.of(readString(in, 64));
            UUID epoch = readUuid(in);
            long generation = in.readLong();
            UUID nonce = readUuid(in);
            Instant issuedAt = Instant.ofEpochMilli(in.readLong());
            int count = in.readInt();
            if (count < 0 || count > CommandCatalog.MAX_ENTRIES) {
                throw new IllegalArgumentException("Invalid command-catalog entry count");
            }
            List<CommandCatalogEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CommandPath path = CommandPath.of(readString(in, 512));
                boolean executable = in.readBoolean();
                entries.add(new CommandCatalogEntry(path, executable,
                        readOptional(in, 96), readOptional(in, 96)));
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing command-catalog data");
            return new CommandCatalog(version, server, epoch, generation, nonce, issuedAt, entries);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Malformed command-catalog frame", malformed);
        }
    }

    private static byte[] mac(byte[] key, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(DOMAIN);
            return mac.doFinal(body);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("Backend key must be at least 32 bytes");
    }

    private static void writeString(DataOutputStream out, String value, int limit) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > limit) throw new IllegalArgumentException("Command-catalog string exceeds limit");
        out.writeShort(data.length);
        out.write(data);
    }

    private static String readString(DataInputStream in, int limit) throws IOException {
        int length = in.readUnsignedShort();
        if (length > limit) throw new IllegalArgumentException("Command-catalog string exceeds limit");
        byte[] data = in.readNBytes(length);
        if (data.length != length) throw new IllegalArgumentException("Truncated command-catalog string");
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeOptional(DataOutputStream out, Optional<String> value, int limit) throws IOException {
        if (value.isEmpty()) out.writeShort(0xffff);
        else writeString(out, value.orElseThrow(), limit);
    }

    private static Optional<String> readOptional(DataInputStream in, int limit) throws IOException {
        int length = in.readUnsignedShort();
        if (length == 0xffff) return Optional.empty();
        if (length > limit) throw new IllegalArgumentException("Command-catalog metadata exceeds limit");
        byte[] data = in.readNBytes(length);
        if (data.length != length) throw new IllegalArgumentException("Truncated command-catalog metadata");
        return Optional.of(new String(data, StandardCharsets.UTF_8));
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
