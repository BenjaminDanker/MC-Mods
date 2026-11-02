package com.silver.chronicle.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Chronicle-only wire envelope, signed with the existing Resonant Message backend key. */
public final class ChronicleProtocol {
    public static final int VERSION = 1;
    public static final int MAX_FRAME_BYTES = 32_768;
    private static final int REQUEST = 1;
    private static final int RESPONSE = 2;
    private static final byte[] REQUEST_DOMAIN = "com.silver.chronicle.request.v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESPONSE_DOMAIN = "com.silver.chronicle.response.v1".getBytes(StandardCharsets.US_ASCII);

    public enum Operation {
        COMPLETE, HISTORY, MINE, SET_PRIVACY, ADMIN_CONCEALED, ADMIN_REANNOUNCE, PROMPT_STATUS,
        ADMIN_EXCLUDE, ADMIN_INCLUDE, ADMIN_EXCLUSIONS, ADMIN_RESET_EVENT, ADMIN_REMOVE_PLAYER
    }

    public record Request(int version, String backendId, UUID playerId, String username, UUID backendEpoch,
                          UUID nonce, long issuedAtMillis, Operation operation, List<String> args, String signature) {
        public Request { args = List.copyOf(args); }
        public Request signed(String value) { return new Request(version, backendId, playerId, username, backendEpoch, nonce, issuedAtMillis, operation, args, value); }
    }

    public record Response(int version, String backendId, UUID playerId, UUID backendEpoch, UUID nonce,
                           boolean success, List<String> lines, String signature) {
        public Response { lines = List.copyOf(lines); }
        public Response signed(String value) { return new Response(version, backendId, playerId, backendEpoch, nonce, success, lines, value); }
    }

    private ChronicleProtocol() { }

    public static Request sign(Request request, byte[] key) {
        return request.signed(Base64.getUrlEncoder().withoutPadding().encodeToString(mac(canonical(request), key)));
    }
    public static boolean verify(Request request, byte[] key) { return verify(request.signature(), canonical(request), key); }
    public static Response sign(Response response, byte[] key) {
        return response.signed(Base64.getUrlEncoder().withoutPadding().encodeToString(mac(canonical(response), key)));
    }
    public static boolean verify(Response response, byte[] key) { return verify(response.signature(), canonical(response), key); }

    public static byte[] encode(Request value) { return frame(REQUEST, out -> writeRequest(out, value, true)); }
    public static byte[] encode(Response value) { return frame(RESPONSE, out -> writeResponse(out, value, true)); }
    public static Request decodeRequest(byte[] bytes) { return readFrame(bytes, REQUEST, ChronicleProtocol::readRequest); }
    public static Response decodeResponse(byte[] bytes) { return readFrame(bytes, RESPONSE, ChronicleProtocol::readResponse); }

    private static byte[] canonical(Request request) { return frame(REQUEST, out -> { out.write(REQUEST_DOMAIN); out.writeByte(0); writeRequest(out, request, false); }); }
    private static byte[] canonical(Response response) { return frame(RESPONSE, out -> { out.write(RESPONSE_DOMAIN); out.writeByte(0); writeResponse(out, response, false); }); }

    private static void writeRequest(DataOutputStream out, Request r, boolean signature) throws IOException {
        out.writeInt(r.version()); text(out, r.backendId()); uuid(out, r.playerId()); text(out, r.username());
        uuid(out, r.backendEpoch()); uuid(out, r.nonce()); out.writeLong(r.issuedAtMillis());
        out.writeByte(r.operation().ordinal()); out.writeInt(r.args().size());
        if (r.args().size() > 16) throw new IOException("Too many Chronicle arguments");
        for (String arg : r.args()) text(out, arg);
        if (signature) text(out, r.signature());
    }
    private static void writeResponse(DataOutputStream out, Response r, boolean signature) throws IOException {
        out.writeInt(r.version()); text(out, r.backendId()); uuid(out, r.playerId()); uuid(out, r.backendEpoch());
        uuid(out, r.nonce()); out.writeBoolean(r.success()); out.writeInt(r.lines().size());
        if (r.lines().size() > 64) throw new IOException("Too many Chronicle response lines");
        for (String line : r.lines()) text(out, line);
        if (signature) text(out, r.signature());
    }
    private static Request readRequest(DataInputStream in) throws IOException {
        int version = in.readInt(); String backend = text(in, 64); UUID player = uuid(in); String name = text(in, 64);
        UUID epoch = uuid(in); UUID nonce = uuid(in); long issued = in.readLong();
        int op = in.readUnsignedByte(); if (op >= Operation.values().length) throw new IOException("Bad operation");
        int count = count(in, 16); List<String> args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) args.add(text(in, 1024));
        return new Request(version, backend, player, name, epoch, nonce, issued, Operation.values()[op], args, text(in, 128));
    }
    private static Response readResponse(DataInputStream in) throws IOException {
        int version = in.readInt(); String backend = text(in, 64); UUID player = uuid(in); UUID epoch = uuid(in);
        UUID nonce = uuid(in); boolean success = in.readBoolean(); int count = count(in, 64);
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) lines.add(text(in, 4096));
        return new Response(version, backend, player, epoch, nonce, success, lines, text(in, 128));
    }
    private static byte[] frame(int type, Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { out.writeInt(0x4348524E); out.writeByte(type); writer.write(out); }
            if (bytes.size() > MAX_FRAME_BYTES) throw new IllegalArgumentException("Chronicle frame too large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Unable to encode Chronicle frame", e); }
    }
    private static <T> T readFrame(byte[] bytes, int expectedType, Reader<T> reader) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FRAME_BYTES) throw new IllegalArgumentException("Invalid Chronicle frame size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 0x4348524E || in.readUnsignedByte() != expectedType) throw new IOException("Wrong Chronicle frame type");
            T value = reader.read(in); if (in.available() != 0) throw new IOException("Trailing Chronicle frame data"); return value;
        } catch (IOException e) { throw new IllegalArgumentException("Malformed Chronicle frame", e); }
    }
    private static int count(DataInputStream in, int max) throws IOException { int value = in.readInt(); if (value < 0 || value > max) throw new IOException("Invalid count"); return value; }
    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] b = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8); if (b.length > 8192) throw new IOException("Text too long"); out.writeInt(b.length); out.write(b);
    }
    private static String text(DataInputStream in, int maxBytes) throws IOException {
        int length = in.readInt(); if (length < 0 || length > maxBytes || length > in.available()) throw new IOException("Invalid text length");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static boolean verify(String signature, byte[] canonical, byte[] key) {
        try { byte[] supplied = Base64.getUrlDecoder().decode(signature); return supplied.length == 32 && MessageDigest.isEqual(mac(canonical, key), supplied); }
        catch (IllegalArgumentException bad) { return false; }
    }
    private static byte[] mac(byte[] bytes, byte[] key) {
        Objects.requireNonNull(key); if (key.length < 32) throw new IllegalArgumentException("Existing backend key must contain at least 256 bits");
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.clone(), "HmacSHA256")); return mac.doFinal(bytes); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("HMAC unavailable", e); }
    }
    @FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface private interface Reader<T> { T read(DataInputStream in) throws IOException; }
}
