package com.silver.authorization;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 with protocol domain separation; use a distinct random key per backend. */
public final class SnapshotSigner {
    private static final String DOMAIN = "com.silver.authorization.snapshot.v2";
    private static final String ALGORITHM = "HmacSHA256";

    public SignedAuthorizationSnapshot sign(AuthorizationSnapshot snapshot, byte[] backendKey) {
        Objects.requireNonNull(snapshot, "snapshot");
        byte[] signature = mac(snapshot, backendKey);
        return new SignedAuthorizationSnapshot(snapshot,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    public boolean verify(SignedAuthorizationSnapshot signed, byte[] backendKey) {
        Objects.requireNonNull(signed, "signed");
        if (signed.signature().length() != 43) return false;
        try {
            byte[] supplied = Base64.getUrlDecoder().decode(signed.signature());
            return MessageDigest.isEqual(mac(signed.snapshot(), backendKey), supplied);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] mac(AuthorizationSnapshot snapshot, byte[] backendKey) {
        Objects.requireNonNull(backendKey, "backendKey");
        if (backendKey.length < 32) throw new IllegalArgumentException("Backend HMAC key must be at least 256 bits");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(backendKey.clone(), ALGORITHM));
            return mac.doFinal(canonicalBytes(snapshot));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Required HMAC algorithm is unavailable", exception);
        }
    }

    private static byte[] canonicalBytes(AuthorizationSnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(DOMAIN.getBytes(StandardCharsets.US_ASCII));
                output.writeByte(0);
                output.writeInt(snapshot.protocolVersion());
                output.writeUTF(snapshot.subject().kind().name());
                output.writeUTF(snapshot.subject().id());
                output.writeUTF(snapshot.serverId().value());
                output.writeLong(snapshot.revision());
                writeUuid(output, snapshot.backendEpoch());
                writeUuid(output, snapshot.nonce());
                writeInstant(output, snapshot.issuedAt());
                writeInstant(output, snapshot.expiresAt());
                output.writeInt(snapshot.rules().size());
                for (PermissionRule rule : snapshot.rules()) {
                    output.writeUTF(rule.pattern().value());
                    output.writeUTF(rule.scope().type().name());
                    output.writeUTF(rule.scope().serverId() == null ? "" : rule.scope().serverId().value());
                    output.writeUTF(rule.origin().name());
                    output.writeUTF(rule.sourceRole().map(RoleId::value).orElse(""));
                    output.writeUTF(rule.effect().name());
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode authorization snapshot", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, java.util.UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeInstant(DataOutputStream output, java.time.Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }
}
