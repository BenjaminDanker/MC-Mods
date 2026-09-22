package com.silver.authorization;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

/** HMAC for backend-originated sync requests, domain-separated from portal and snapshot signing. */
public final class BackendRequestSigner {
    private static final String DOMAIN = "com.silver.authorization.backend-request.v2";

    public AuthorizationSyncRequest sign(AuthorizationSyncRequest request, byte[] backendKey) {
        return request.withSignature(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DomainSeparatedHmac.sign(DOMAIN, canonical(request), backendKey)));
    }

    public boolean verify(AuthorizationSyncRequest request, byte[] backendKey) {
        try {
            byte[] supplied = Base64.getUrlDecoder().decode(request.signature());
            return DomainSeparatedHmac.verify(DOMAIN, canonical(request), backendKey, supplied);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static byte[] canonical(AuthorizationSyncRequest request) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(request.protocolVersion());
                out.writeUTF(request.backendId());
                out.writeUTF(request.subject().kind().name());
                out.writeUTF(request.subject().id());
                out.writeUTF(request.serverId().value());
                out.writeLong(request.backendEpoch().getMostSignificantBits());
                out.writeLong(request.backendEpoch().getLeastSignificantBits());
                out.writeLong(request.nonce().getMostSignificantBits());
                out.writeLong(request.nonce().getLeastSignificantBits());
                out.writeLong(request.issuedAt().getEpochSecond());
                out.writeInt(request.issuedAt().getNano());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode authorization request", impossible);
        }
    }

}
