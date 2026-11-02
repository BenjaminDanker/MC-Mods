package com.silver.authorization.fabric;

import com.silver.authorization.Authorization;
import com.silver.authorization.AuthorizationSnapshotStore;
import com.silver.authorization.ServerId;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric-side network authorization module entrypoint. */
public final class NetworkAuthorizationMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("network_authorization");
    private static volatile NetworkAuthorizationRuntime runtime;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(AuthorizationPayload.ID, AuthorizationPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AuthorizationPayload.ID, AuthorizationPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CommandCatalogPayload.ID, CommandCatalogPayload.CODEC);
        Path configDir = FabricLoader.getInstance().getConfigDir();
        FabricAuthorizationConfig config = FabricAuthorizationConfig.load(configDir, LOGGER);
        CommandPolicyRuntime.initialize(LOGGER);
        NetworkAuthorizationRuntime initialized = new NetworkAuthorizationRuntime(config, LOGGER);
        initialized.register();
        runtime = initialized;
        CommandTreeDiagnostic.register(LOGGER);
        OpenPacPermissionProvider.register(LOGGER);
        LOGGER.info("[NetworkAuthorization] Local evaluator ready; configured server={}",
                config.serverId().map(ServerId::value).orElse("<unset>"));
    }

    public static Authorization authorization() {
        return runtime == null ? (subject, permission, server) ->
                com.silver.authorization.AuthorizationDecision.defaultDeny(permission, server, 0) : runtime;
    }

    public static ServerId serverId() {
        return runtime == null ? ServerId.of("unconfigured") : runtime.serverId();
    }

    public static Optional<AuthorizationSnapshotStore> snapshotStore() {
        return runtime == null ? Optional.empty() : runtime.snapshotStore();
    }
}
