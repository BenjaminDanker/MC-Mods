package com.silver.portalprotocol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PortalProtocolMod implements ModInitializer {
    public static final String MOD_ID = "mc-portal-protocol";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(PortalRequestPayload.PACKET_ID, PortalRequestPayload.codec);
        LOGGER.info("Registered wakeuplobby:portal_request payload protocol");
    }
}
