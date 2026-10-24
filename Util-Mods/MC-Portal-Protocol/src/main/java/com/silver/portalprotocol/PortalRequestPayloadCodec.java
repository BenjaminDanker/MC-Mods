package com.silver.portalprotocol;

import com.silver.authorization.PortalRequestCodec;
import java.util.UUID;

/** The portal request wire format lives in Authorization-Common's {@link PortalRequestCodec}, shared with the proxy. */
public final class PortalRequestPayloadCodec {
    public static final byte VERSION_1 = PortalRequestCodec.VERSION_1;

    private PortalRequestPayloadCodec() {
    }

    public static byte[] encodeUnsigned(UUID playerId, String targetServer, String arrivalPortal, long issuedAtMs, String nonce) {
        return PortalRequestCodec.encodeUnsigned(playerId, targetServer, arrivalPortal, issuedAtMs, nonce);
    }

    public static byte[] encodeSigned(UUID playerId, String targetServer, String arrivalPortal, long issuedAtMs,
                                      String nonce, byte[] signature) {
        return PortalRequestCodec.encodeSigned(playerId, targetServer, arrivalPortal, issuedAtMs, nonce, signature);
    }

    public static String generateNonce() {
        return PortalRequestCodec.generateNonce();
    }
}
