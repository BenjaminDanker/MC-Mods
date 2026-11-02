package com.silver.authorization;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wire format of a backend -> proxy portal request, shared by the backends (which sign and send it) and the proxy
 * (which decodes and verifies it), so the two sides cannot drift apart.
 *
 * Version 1: byte version, long issuedAtMs, UUID (msb, lsb), targetServer, arrivalPortal (may be empty), nonce,
 * then the signature bytes. Strings and byte arrays are a varint length followed by the bytes; longs are big-endian.
 * The signature covers the unsigned prefix ({@link #encodeUnsigned}).
 */
public final class PortalRequestCodec {
    public static final byte VERSION_1 = 1;
    private static final int MAX_SIGNATURE_BYTES = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PortalRequestCodec() {
    }

    public static byte[] encodeUnsigned(UUID playerId, String targetServer, String arrivalPortal, long issuedAtMs, String nonce) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(arrivalPortal, "arrivalPortal");
        Objects.requireNonNull(nonce, "nonce");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(VERSION_1);
        writeLong(out, issuedAtMs);
        writeLong(out, playerId.getMostSignificantBits());
        writeLong(out, playerId.getLeastSignificantBits());
        writeBytes(out, targetServer.getBytes(StandardCharsets.UTF_8));
        writeBytes(out, arrivalPortal.getBytes(StandardCharsets.UTF_8));
        writeBytes(out, nonce.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    public static byte[] encodeUnsigned(PortalRequestUnsigned request) {
        Objects.requireNonNull(request, "request");
        return encodeUnsigned(request.playerId(), request.targetServer(), request.arrivalPortal(),
                request.issuedAtMs(), request.nonce());
    }

    public static byte[] encodeSigned(UUID playerId, String targetServer, String arrivalPortal, long issuedAtMs,
                                      String nonce, byte[] signature) {
        Objects.requireNonNull(signature, "signature");
        byte[] unsigned = encodeUnsigned(playerId, targetServer, arrivalPortal, issuedAtMs, nonce);
        ByteArrayOutputStream out = new ByteArrayOutputStream(unsigned.length + 64);
        out.writeBytes(unsigned);
        writeBytes(out, signature);
        return out.toByteArray();
    }

    public static byte[] encode(PortalRequest request) {
        Objects.requireNonNull(request, "request");
        return encodeSigned(request.playerId(), request.targetServer(), request.arrivalPortal(),
                request.issuedAtMs(), request.nonce(), request.signature());
    }

    /** Empty for an unknown version, a blank target or nonce; throws on a truncated or oversized payload. */
    public static Optional<PortalRequest> decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }
        ByteBuffer in = ByteBuffer.wrap(payload);
        try {
            if (in.get() != VERSION_1) {
                return Optional.empty();
            }
            long issuedAtMs = in.getLong();
            UUID playerId = new UUID(in.getLong(), in.getLong());
            String target = readString(in);
            String arrivalPortal = readString(in);
            String nonce = readString(in);
            byte[] signature = readBytes(in, MAX_SIGNATURE_BYTES);
            if (target.isBlank() || nonce.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PortalRequest(playerId, target, arrivalPortal, issuedAtMs, nonce, signature));
        } catch (java.nio.BufferUnderflowException truncated) {
            throw new IllegalArgumentException("Truncated portal request", truncated);
        }
    }

    public static String generateNonce() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int index = 7; index >= 0; index--) {
            out.write((int) (value >>> (index * 8)) & 0xFF);
        }
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        int value = safe.length;
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        out.writeBytes(safe);
    }

    private static String readString(ByteBuffer in) {
        return new String(readBytes(in, in.remaining()), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(ByteBuffer in, int maxLength) {
        int length = readVarInt(in);
        if (length < 0 || length > maxLength) {
            throw new IllegalArgumentException("Bad length: " + length);
        }
        byte[] bytes = new byte[length];
        in.get(bytes);
        return bytes;
    }

    private static int readVarInt(ByteBuffer in) {
        int result = 0;
        for (int position = 0; position < 5; position++) {
            byte read = in.get();
            result |= (read & 0x7F) << (7 * position);
            if ((read & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalArgumentException("VarInt is too big");
    }

    public record PortalRequest(UUID playerId, String targetServer, String arrivalPortal, long issuedAtMs,
                                String nonce, byte[] signature) {
        public PortalRequest {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(targetServer, "targetServer");
            Objects.requireNonNull(arrivalPortal, "arrivalPortal");
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(signature, "signature");
        }

        public PortalRequestUnsigned unsigned() {
            return new PortalRequestUnsigned(playerId, targetServer, arrivalPortal, issuedAtMs, nonce);
        }
    }

    public record PortalRequestUnsigned(UUID playerId, String targetServer, String arrivalPortal, long issuedAtMs,
                                        String nonce) {
        public PortalRequestUnsigned {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(targetServer, "targetServer");
            Objects.requireNonNull(arrivalPortal, "arrivalPortal");
            Objects.requireNonNull(nonce, "nonce");
        }
    }
}
