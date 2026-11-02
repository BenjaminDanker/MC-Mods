package com.silver.authorization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bounded binary framing for the dedicated Velocity/backend authorization channel. */
public final class AuthorizationProtocolCodec {
    public static final int MAX_FRAME_BYTES = 256 * 1024;
    private static final int REQUEST = 1;
    private static final int SNAPSHOT = 2;

    private AuthorizationProtocolCodec() {}

    public static byte[] encodeRequest(AuthorizationSyncRequest request) {
        return write(out -> {
            out.writeByte(REQUEST);
            out.writeInt(request.protocolVersion());
            out.writeUTF(request.backendId());
            writeSubject(out, request.subject());
            out.writeUTF(request.serverId().value());
            writeUuid(out, request.backendEpoch());
            writeUuid(out, request.nonce());
            writeInstant(out, request.issuedAt());
            out.writeUTF(request.signature());
        });
    }

    public static AuthorizationSyncRequest decodeRequest(byte[] bytes) {
        return read(bytes, in -> {
            if (in.readUnsignedByte() != REQUEST) throw new IOException("Wrong authorization frame type");
            int version = in.readInt();
            String backend = boundedUtf(in, 64);
            AuthorizationSubject subject = readSubject(in);
            ServerId server = ServerId.of(boundedUtf(in, 64));
            UUID epoch = readUuid(in);
            UUID nonce = readUuid(in);
            Instant issued = readInstant(in);
            String signature = boundedUtf(in, 64);
            return new AuthorizationSyncRequest(version, backend, subject, server, epoch, nonce, issued, signature);
        });
    }

    public static byte[] encodeSnapshot(SignedAuthorizationSnapshot signed) {
        AuthorizationSnapshot snapshot = signed.snapshot();
        return write(out -> {
            out.writeByte(SNAPSHOT);
            out.writeInt(snapshot.protocolVersion());
            writeSubject(out, snapshot.subject());
            out.writeUTF(snapshot.serverId().value());
            out.writeLong(snapshot.revision());
            writeUuid(out, snapshot.backendEpoch());
            writeUuid(out, snapshot.nonce());
            writeInstant(out, snapshot.issuedAt());
            writeInstant(out, snapshot.expiresAt());
            out.writeInt(snapshot.rules().size());
            for (PermissionRule rule : snapshot.rules()) {
                out.writeUTF(rule.pattern().value());
                out.writeUTF(rule.scope().type().name());
                out.writeUTF(rule.scope().serverId() == null ? "" : rule.scope().serverId().value());
                out.writeUTF(rule.origin().name());
                out.writeUTF(rule.sourceRole().map(RoleId::value).orElse(""));
                out.writeUTF(rule.effect().name());
            }
            out.writeUTF(signed.signature());
        });
    }

    public static SignedAuthorizationSnapshot decodeSnapshot(byte[] bytes) {
        return read(bytes, in -> {
            if (in.readUnsignedByte() != SNAPSHOT) throw new IOException("Wrong authorization frame type");
            int version = in.readInt();
            AuthorizationSubject subject = readSubject(in);
            ServerId server = ServerId.of(boundedUtf(in, 64));
            long revision = in.readLong();
            UUID epoch = readUuid(in);
            UUID nonce = readUuid(in);
            Instant issued = readInstant(in);
            Instant expires = readInstant(in);
            int count = in.readInt();
            if (count < 0 || count > AuthorizationSnapshot.MAX_RULES) throw new IOException("Invalid rule count");
            List<PermissionRule> rules = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                PermissionPattern pattern = PermissionPattern.of(boundedUtf(in, PermissionNode.MAX_LENGTH));
                String scopeType = boundedUtf(in, 16);
                String scopeServer = boundedUtf(in, 64);
                AuthorizationScope scope = switch (AuthorizationScope.Type.valueOf(scopeType)) {
                    case GLOBAL -> AuthorizationScope.global();
                    case SERVER -> AuthorizationScope.server(ServerId.of(scopeServer));
                };
                RuleOrigin origin = RuleOrigin.valueOf(boundedUtf(in, 24));
                String role = boundedUtf(in, 64);
                PermissionEffect effect = PermissionEffect.valueOf(boundedUtf(in, 16));
                rules.add(new PermissionRule(pattern, scope, origin, effect,
                        role.isEmpty() ? Optional.empty() : Optional.of(RoleId.of(role))));
            }
            String signature = boundedUtf(in, 64);
            AuthorizationSnapshot snapshot = new AuthorizationSnapshot(version, subject, server,
                    revision, epoch, nonce, issued, expires, rules);
            return new SignedAuthorizationSnapshot(snapshot, signature);
        });
    }

    private static void writeSubject(DataOutputStream out, AuthorizationSubject subject) throws IOException {
        out.writeUTF(subject.kind().name());
        out.writeUTF(subject.id());
    }

    private static AuthorizationSubject readSubject(DataInputStream in) throws IOException {
        AuthorizationSubject.Kind kind = AuthorizationSubject.Kind.valueOf(boundedUtf(in, 24));
        String id = boundedUtf(in, 128);
        return new AuthorizationSubject(kind, id);
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeInstant(DataOutputStream out, Instant time) throws IOException {
        out.writeLong(time.getEpochSecond());
        out.writeInt(time.getNano());
    }

    private static Instant readInstant(DataInputStream in) throws IOException {
        return Instant.ofEpochSecond(in.readLong(), in.readInt());
    }

    private static String boundedUtf(DataInputStream in, int maxChars) throws IOException {
        String result = in.readUTF();
        if (result.length() > maxChars) throw new IOException("Authorization field too long");
        return result;
    }

    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); }
            if (bytes.size() > MAX_FRAME_BYTES) throw new IllegalArgumentException("Authorization frame too large");
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to encode authorization frame", failure);
        }
    }

    private static <T> T read(byte[] bytes, Reader<T> reader) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("Invalid authorization frame size");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            T result = reader.read(in);
            if (in.available() != 0) throw new IOException("Trailing authorization frame bytes");
            return result;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Malformed authorization frame", failure);
        }
    }

    @FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface private interface Reader<T> { T read(DataInputStream in) throws IOException; }
}
