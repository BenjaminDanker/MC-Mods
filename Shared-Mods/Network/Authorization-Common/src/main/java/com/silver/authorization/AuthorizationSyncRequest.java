package com.silver.authorization;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Backend-authenticated request for the target player's current server-scoped snapshot. */
public record AuthorizationSyncRequest(
        int protocolVersion,
        String backendId,
        AuthorizationSubject subject,
        ServerId serverId,
        UUID backendEpoch,
        UUID nonce,
        Instant issuedAt,
        String signature) {
    public AuthorizationSyncRequest {
        if (protocolVersion != AuthorizationSnapshot.CURRENT_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported authorization protocol version");
        }
        Objects.requireNonNull(backendId, "backendId");
        if (!backendId.equals(serverId.value())) throw new IllegalArgumentException("Backend ID and server ID differ");
        Objects.requireNonNull(subject, "subject");
        if (subject.kind() != AuthorizationSubject.Kind.PLAYER) {
            throw new IllegalArgumentException("Snapshot sync requests are player-bound");
        }
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(backendEpoch, "backendEpoch");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(signature, "signature");
        if (signature.length() != 43) throw new IllegalArgumentException("Invalid HMAC signature length");
    }

    public AuthorizationSyncRequest withSignature(String value) {
        return new AuthorizationSyncRequest(protocolVersion, backendId, subject, serverId,
                backendEpoch, nonce, issuedAt, value);
    }

    public static AuthorizationSyncRequest unsigned(String backendId, UUID player, ServerId serverId,
                                                    UUID backendEpoch, UUID nonce, Instant issuedAt) {
        return new AuthorizationSyncRequest(AuthorizationSnapshot.CURRENT_PROTOCOL_VERSION, backendId,
                AuthorizationSubject.player(player), serverId, backendEpoch, nonce, issuedAt, "A".repeat(43));
    }
}
