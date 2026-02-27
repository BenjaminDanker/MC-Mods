package com.silver.viewextend.client;

import com.silver.viewextend.ViewExtendMod;
import net.fabricmc.api.ClientModInitializer;

public class ViewExtendClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ViewExtendMod.LOGGER.info("View Extend client diagnostics initialized (client-debug={})", ClientPacketDebugTracker.isEnabled());
    }
}
