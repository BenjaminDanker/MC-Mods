package com.silver.portalprotocol;

import com.silver.authorization.NetworkShared;

/** The secret portal requests are signed with: the network-wide one when this host has it, else the mod's own. */
public final class PortalRequestSecret {
    private PortalRequestSecret() {
    }

    public static String resolve(String configured) {
        return NetworkShared.get("portal_request_secret", configured == null ? "" : configured);
    }
}
