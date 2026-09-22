package com.silver.chronicle.fabric;

import com.silver.chronicle.fabric.command.ChronicleCommands;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChronicleMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("chronicle");
    private static ChronicleRuntime runtime;

    @Override public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(ChroniclePayload.ID, ChroniclePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChroniclePayload.ID, ChroniclePayload.CODEC);
        BackendIdentity identity = BackendIdentity.load(LOGGER);
        runtime = new ChronicleRuntime(identity, LOGGER);
        runtime.register();
        ChronicleEvents.register(runtime);
        ChronicleCommands.register(runtime);
        LOGGER.info("[Chronicle] Fabric hooks registered backend={}", identity.backendId().isBlank() ? "<unavailable>" : identity.backendId());
    }

    public static void complete(ServerPlayer player, String eventId) {
        if (runtime != null) runtime.complete(player, eventId);
    }
    static void receive(byte[] bytes, ServerPlayer player) { if (runtime != null) runtime.receive(bytes, player); }
    public static UUID backendEpoch() { return runtime == null ? new UUID(0, 0) : runtime.backendEpoch(); }
}
