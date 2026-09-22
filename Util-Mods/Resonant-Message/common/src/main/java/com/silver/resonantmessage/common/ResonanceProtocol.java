package com.silver.resonantmessage.common;

import com.silver.authorization.DomainSeparatedHmac;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public final class ResonanceProtocol {
    public static final int VERSION = 1;
    public static final int MAX_FRAME_BYTES = 8192;
    public static final int MAX_MESSAGE_CODE_POINTS = 256;
    private static final int REQUEST = 1;
    private static final int ACKNOWLEDGEMENT = 2;
    // Shares each backend's Network Authorization key but keeps the request and acknowledgement
    // signatures isolated from every other protocol using that key.
    private static final String REQUEST_DOMAIN = "resonant-message/v1";
    private static final String ACK_DOMAIN = "resonant-message/v1/ack";

    private ResonanceProtocol() {}

    public record Request(int protocolVersion, String backendId, UUID playerId, String message,
                         UUID backendEpoch, UUID nonce, long issuedAtEpochMillis, String signature) {
        public Request withSignature(String value) {
            return new Request(protocolVersion, backendId, playerId, message, backendEpoch, nonce, issuedAtEpochMillis, value);
        }
    }

    public record Acknowledgement(int protocolVersion, String backendId, UUID playerId,
                                   UUID backendEpoch, UUID nonce, boolean accepted, String signature) {
        public Acknowledgement withSignature(String value) {
            return new Acknowledgement(protocolVersion, backendId, playerId, backendEpoch, nonce, accepted, value);
        }
    }

    public static Request sign(Request request, byte[] key) {
        return request.withSignature(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DomainSeparatedHmac.sign(REQUEST_DOMAIN, canonicalRequest(request), key)));
    }

    public static boolean verify(Request request, byte[] key) {
        return verifySignature(request.signature(), REQUEST_DOMAIN, canonicalRequest(request), key);
    }

    public static Acknowledgement sign(Acknowledgement acknowledgement, byte[] key) {
        return acknowledgement.withSignature(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DomainSeparatedHmac.sign(ACK_DOMAIN, canonicalAcknowledgement(acknowledgement), key)));
    }

    public static boolean verify(Acknowledgement acknowledgement, byte[] key) {
        return verifySignature(acknowledgement.signature(), ACK_DOMAIN, canonicalAcknowledgement(acknowledgement), key);
    }

    public static byte[] encode(Request request) {
        return write(out -> {
            out.writeByte(REQUEST);
            writeRequest(out, request, true);
        });
    }

    public static Request decodeRequest(byte[] bytes) {
        return read(bytes, in -> {
            if (in.readUnsignedByte() != REQUEST) throw new IOException("Unexpected frame type");
            return readRequest(in);
        });
    }

    public static byte[] encode(Acknowledgement acknowledgement) {
        return write(out -> {
            out.writeByte(ACKNOWLEDGEMENT);
            writeAcknowledgement(out, acknowledgement, true);
        });
    }

    public static Acknowledgement decodeAcknowledgement(byte[] bytes) {
        return read(bytes, in -> {
            if (in.readUnsignedByte() != ACKNOWLEDGEMENT) throw new IOException("Unexpected frame type");
            return readAcknowledgement(in);
        });
    }

    public static boolean validBackendId(String backendId) {
        return backendId != null && backendId.matches("[a-z0-9_-]{1,64}");
    }

    public static boolean validMessage(String message) {
        if (message == null || message.isBlank()
                || message.codePointCount(0, message.length()) > MAX_MESSAGE_CODE_POINTS) return false;
        for (int i = 0; i < message.length();) {
            int codePoint = message.codePointAt(i);
            if (codePoint == '\n' || codePoint == '\r' || codePoint == 0
                    || Character.isISOControl(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    private static byte[] canonicalRequest(Request request) {
        return write(out -> writeRequest(out, request, false));
    }

    private static byte[] canonicalAcknowledgement(Acknowledgement acknowledgement) {
        return write(out -> writeAcknowledgement(out, acknowledgement, false));
    }

    private static void writeRequest(DataOutputStream out, Request request, boolean withSignature) throws IOException {
        out.writeInt(request.protocolVersion());
        out.writeUTF(request.backendId());
        writeUuid(out, request.playerId());
        out.writeUTF(request.message());
        writeUuid(out, request.backendEpoch());
        writeUuid(out, request.nonce());
        out.writeLong(request.issuedAtEpochMillis());
        if (withSignature) out.writeUTF(request.signature());
    }

    private static Request readRequest(DataInputStream in) throws IOException {
        int version = in.readInt();
        String backend = boundedUtf(in, 64);
        UUID player = readUuid(in);
        String message = boundedUtf(in, 2048);
        UUID epoch = readUuid(in);
        UUID nonce = readUuid(in);
        long issuedAt = in.readLong();
        String signature = boundedUtf(in, 64);
        return new Request(version, backend, player, message, epoch, nonce, issuedAt, signature);
    }

    private static void writeAcknowledgement(DataOutputStream out, Acknowledgement acknowledgement,
                                             boolean withSignature) throws IOException {
        out.writeInt(acknowledgement.protocolVersion());
        out.writeUTF(acknowledgement.backendId());
        writeUuid(out, acknowledgement.playerId());
        writeUuid(out, acknowledgement.backendEpoch());
        writeUuid(out, acknowledgement.nonce());
        out.writeBoolean(acknowledgement.accepted());
        if (withSignature) out.writeUTF(acknowledgement.signature());
    }

    private static Acknowledgement readAcknowledgement(DataInputStream in) throws IOException {
        int version = in.readInt();
        String backend = boundedUtf(in, 64);
        UUID player = readUuid(in);
        UUID epoch = readUuid(in);
        UUID nonce = readUuid(in);
        boolean accepted = in.readBoolean();
        String signature = boundedUtf(in, 64);
        return new Acknowledgement(version, backend, player, epoch, nonce, accepted, signature);
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static String boundedUtf(DataInputStream in, int maxCharacters) throws IOException {
        String value = in.readUTF();
        if (value.length() > maxCharacters) throw new IOException("Field exceeds character bound");
        return value;
    }

    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); }
            if (bytes.size() <= 0 || bytes.size() > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("Invalid Resonance frame size");
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Could not encode Resonance frame", failure);
        }
    }

    private static <T> T read(byte[] bytes, Reader<T> reader) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("Invalid Resonance frame size");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            T value = reader.read(in);
            if (in.available() != 0) throw new IOException("Trailing frame bytes");
            return value;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Malformed Resonance frame", failure);
        }
    }

    private static boolean verifySignature(String encoded, String domain, byte[] canonical, byte[] key) {
        try {
            byte[] supplied = Base64.getUrlDecoder().decode(encoded);
            return DomainSeparatedHmac.verify(domain, canonical, key, supplied);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface private interface Reader<T> { T read(DataInputStream in) throws IOException; }
}
