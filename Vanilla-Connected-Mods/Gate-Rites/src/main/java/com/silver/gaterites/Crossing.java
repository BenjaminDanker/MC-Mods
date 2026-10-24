package com.silver.gaterites;

import de.michiruf.serverportals.ServerPortalsMod;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends a traveller to the gate's destination through the proxy: the same signed request a server-portals portal makes. */
final class Crossing {
    private static final Logger LOGGER = LoggerFactory.getLogger("gate-rites");

    private final GateConfig config;

    Crossing(GateConfig config) {
        this.config = config;
    }

    /** False when the request could not be made (the gate then stays as it is); the reason is logged as an ALERT. */
    boolean send(ServerPlayer traveller) {
        if (config.arrivalPortal.isBlank()) {
            LOGGER.error("ALERT: gate-rites.json has no arrivalPortal; {} cannot cross", traveller.getName().getString());
            return false;
        }
        boolean queued = ServerPortalsMod.transfer(traveller, config.targetServer, config.arrivalPortal, "gate-rites " + config.role + " gate");
        if (!queued) {
            LOGGER.error("ALERT: the crossing for {} to {} was not queued", traveller.getName().getString(), config.targetServer);
        }
        return queued;
    }
}
