package com.silver.wakeuplobby.portal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Must match WakeUpLobby's PortalRequestPayloadCodec.
 */
public final class PortalRequestPayloadCodec {
    public static final byte VERSION_1 = 1;

    private PortalRequestPayloadCodec() {
    }

    public static byte[] encodeUnsigned(UUID playerId,
                                        String targetServer,
                                        String sourcePortal,
                                        long issuedAtMs,
                                        String nonce) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(sourcePortal, "sourcePortal");
        Objects.requireNonNull(nonce, "nonce");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(VERSION_1);
        writeLong(out, issuedAtMs);
        writeLong(out, playerId.getMostSignificantBits());
        writeLong(out, playerId.getLeastSignificantBits());
        writeString(out, targetServer);
        writeString(out, sourcePortal);
        writeString(out, nonce);
        return out.toByteArray();
    }

    public static byte[] encodeSigned(UUID playerId,
                                      String targetServer,
                                      String sourcePortal,
                                      long issuedAtMs,
                                      String nonce,
                                      byte[] signature) {
        Objects.requireNonNull(signature, "signature");

        byte[] unsigned = encodeUnsigned(playerId, targetServer, sourcePortal, issuedAtMs, nonce);
        ByteArrayOutputStream out = new ByteArrayOutputStream(unsigned.length + 64);
        out.writeBytes(unsigned);
        writeBytes(out, signature);
        return out.toByteArray();
    }

    public static String generateNonce() {
        byte[] bytes = new byte[18];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        String safe = value == null ? "" : value;
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        writeVarInt(out, safe.length);
        out.writeBytes(safe);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) (value >>> (i * 8)) & 0xFF);
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }
}
